import { XMLParser, XMLValidator } from "npm:fast-xml-parser@5.11.2";
import { BlobReader, ZipReader } from "npm:@zip.js/zip.js@2.24.0";

export const MAX_FILE_BYTES = 8 * 1024 * 1024;
export const MAX_ENTRIES = 250;
export const MAX_ENTRY_BYTES = 8 * 1024 * 1024;
export const MAX_TOTAL_EXPANDED_BYTES = 32 * 1024 * 1024;
export const MAX_COMPRESSION_RATIO = 100;
const MAX_XML_BYTES = 1024 * 1024;

export class EpubValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "EpubValidationError";
  }
}

export function isSafeArchivePath(path: string): boolean {
  if (!path || path.includes("\\") || path.includes("\0") || path.startsWith("/")) return false;
  if (/^[a-z][a-z0-9+.-]*:/iu.test(path)) return false;
  const segments = path.split("/");
  return segments.every((segment, index) =>
    segment.length > 0 && segment !== "." && segment !== ".." &&
    !(index === segments.length - 1 && segment === "")
  );
}

function assertSafePath(path: string): void {
  if (!isSafeArchivePath(path)) throw new EpubValidationError("unsafe_archive_path");
}

function findLocalName(value: unknown, wanted: string): unknown[] {
  const found: unknown[] = [];
  const visit = (node: unknown): void => {
    if (!node || typeof node !== "object") return;
    for (const [key, child] of Object.entries(node)) {
      if (key.split(":").at(-1) === wanted) found.push(child);
      if (Array.isArray(child)) child.forEach(visit);
      else visit(child);
    }
  };
  visit(value);
  return found;
}

function textValue(value: unknown): string {
  if (typeof value === "string") return value.trim();
  if (value && typeof value === "object" && "#text" in value) {
    return textValue((value as Record<string, unknown>)["#text"]);
  }
  return "";
}

function parseXml(xml: string): unknown {
  if (/<!DOCTYPE|<!ENTITY/iu.test(xml)) {
    throw new EpubValidationError("xml_doctype_not_allowed");
  }
  const validation = XMLValidator.validate(xml, { allowBooleanAttributes: false });
  if (validation !== true) throw new EpubValidationError("invalid_xml");
  const parser = new XMLParser({
    ignoreAttributes: false,
    attributeNamePrefix: "@_",
    removeNSPrefix: true,
    processEntities: false,
    parseTagValue: false,
    trimValues: true,
  });
  return parser.parse(xml);
}

async function readEntry(
  entry: Awaited<ReturnType<InstanceType<typeof ZipReader>["getEntries"]>>[number],
  maxBytes: number,
  collect: boolean,
): Promise<Uint8Array | null> {
  let total = 0;
  const chunks: Uint8Array[] = [];
  const sink = new WritableStream<Uint8Array>({
    write(chunk) {
      total += chunk.byteLength;
      if (total > maxBytes) throw new EpubValidationError("expanded_entry_too_large");
      if (collect) chunks.push(chunk.slice());
    },
  });
  await entry.getData(sink, {
    checkCrc32: true,
    checkLocalDirectory: true,
    checkLocalFilename: true,
    checkAmbiguity: true,
    checkOverlappingEntry: true,
  });
  if (!collect) return null;
  const output = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    output.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return output;
}

function resolveOpfPath(container: unknown): string {
  const rootfiles = findLocalName(container, "rootfile");
  for (const rootfile of rootfiles) {
    if (rootfile && typeof rootfile === "object") {
      const path = (rootfile as Record<string, unknown>)["@_full-path"];
      if (typeof path === "string" && path.trim()) return path.trim();
    }
  }
  throw new EpubValidationError("container_rootfile_missing");
}

function validateOpf(opf: unknown): void {
  if (findLocalName(opf, "package").length === 0) {
    throw new EpubValidationError("opf_package_missing");
  }
  const titles = findLocalName(opf, "title").map(textValue).filter(Boolean);
  const creators = findLocalName(opf, "creator").map(textValue).filter(Boolean);
  if (titles.length === 0 || creators.length === 0) {
    throw new EpubValidationError("opf_required_metadata_missing");
  }
  if (findLocalName(opf, "manifest").length === 0 || findLocalName(opf, "spine").length === 0) {
    throw new EpubValidationError("opf_manifest_or_spine_missing");
  }
}

export async function validateEpub(blob: Blob): Promise<string> {
  if (blob.size < 100 || blob.size > MAX_FILE_BYTES) {
    throw new EpubValidationError("file_size_out_of_range");
  }

  const reader = new ZipReader(new BlobReader(blob), {
    strictness: "strict",
    checkCrc32: true,
    checkOverlappingEntry: true,
  });

  try {
    const entries = await reader.getEntries();
    if (entries.length < 3 || entries.length > MAX_ENTRIES) {
      throw new EpubValidationError("archive_entry_count_out_of_range");
    }

    const names = new Set<string>();
    let expandedTotal = 0;
    let containerEntry: typeof entries[number] | undefined;
    let opfEntry: typeof entries[number] | undefined;

    for (const [index, entry] of entries.entries()) {
      const name = entry.filename;
      assertSafePath(name);
      const collisionKey = name.normalize("NFC").toLocaleLowerCase("en-US");
      if (names.has(collisionKey)) throw new EpubValidationError("duplicate_archive_path");
      names.add(collisionKey);

      if (entry.directory || entry.encrypted || entry.symlink || entry.zip64 ||
          entry.diskNumberStart !== 0 || ![0, 8].includes(entry.compressionMethod)) {
        throw new EpubValidationError("unsupported_archive_entry");
      }
      if (!Number.isSafeInteger(entry.compressedSize) ||
          !Number.isSafeInteger(entry.uncompressedSize) ||
          entry.compressedSize < 0 || entry.uncompressedSize < 0 ||
          entry.uncompressedSize > MAX_ENTRY_BYTES) {
        throw new EpubValidationError("archive_entry_size_invalid");
      }
      expandedTotal += entry.uncompressedSize;
      if (expandedTotal > MAX_TOTAL_EXPANDED_BYTES) {
        throw new EpubValidationError("archive_expansion_limit_exceeded");
      }
      if (entry.uncompressedSize > 0 &&
          (entry.compressedSize === 0 ||
           entry.uncompressedSize / Math.max(entry.compressedSize, 1) > MAX_COMPRESSION_RATIO)) {
        throw new EpubValidationError("archive_compression_ratio_exceeded");
      }
      if (index === 0 && (name !== "mimetype" || entry.compressionMethod !== 0 ||
          entry.compressedSize !== entry.uncompressedSize)) {
        throw new EpubValidationError("epub_mimetype_must_be_first_and_uncompressed");
      }
      if (name === "META-INF/container.xml") containerEntry = entry;
    }

    if (entries[0].filename !== "mimetype") {
      throw new EpubValidationError("epub_mimetype_missing");
    }

    const mimeBytes = await readEntry(entries[0], 64, true);
    if (new TextDecoder("utf-8", { fatal: true }).decode(mimeBytes!) !== "application/epub+zip") {
      throw new EpubValidationError("epub_mimetype_invalid");
    }
    if (!containerEntry) throw new EpubValidationError("container_xml_missing");

    const containerBytes = await readEntry(containerEntry, MAX_XML_BYTES, true);
    const containerText = new TextDecoder("utf-8", { fatal: true }).decode(containerBytes!);
    const container = parseXml(containerText);
    const opfPath = resolveOpfPath(container);
    assertSafePath(opfPath);
    opfEntry = entries.find((entry) => entry.filename === opfPath);
    if (!opfEntry) throw new EpubValidationError("opf_file_missing");

    const opfBytes = await readEntry(opfEntry, MAX_XML_BYTES, true);
    const opfText = new TextDecoder("utf-8", { fatal: true }).decode(opfBytes!);
    validateOpf(parseXml(opfText));

    // Decompress every remaining entry through a bounded sink. This verifies
    // CRC/local headers and ensures the declared archive sizes are not trusted.
    for (const entry of entries) {
      if (entry === entries[0] || entry === containerEntry || entry === opfEntry) continue;
      await readEntry(entry, entry.uncompressedSize, false);
    }
  } catch (error) {
    if (error instanceof EpubValidationError) throw error;
    throw new EpubValidationError("invalid_or_corrupt_epub_archive");
  } finally {
    await reader.close();
  }

  const digest = await crypto.subtle.digest("SHA-256", await blob.arrayBuffer());
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}
