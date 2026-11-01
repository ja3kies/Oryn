import {
  InputError,
  parseCreateUploadInput,
  RIGHTS_DECLARATION_VERSION,
} from "./validation.ts";

Deno.test("accepts valid metadata and explicit rights confirmation", () => {
  const result = parseCreateUploadInput({
    title: "Les Misérables",
    author: "Victor Hugo",
    language: "fr-FR",
    rightsConfirmed: true,
  });

  if (result.title !== "Les Misérables" || result.language !== "fr-FR") {
    throw new Error("Valid metadata was not preserved");
  }
  if (RIGHTS_DECLARATION_VERSION.length > 32) {
    throw new Error("Rights declaration version exceeds database constraint");
  }
});

Deno.test("trims metadata before storing", () => {
  const result = parseCreateUploadInput({
    title: "  Dune  ",
    author: "  Frank Herbert ",
    language: "en",
    rightsConfirmed: true,
  });

  if (result.title !== "Dune" || result.author !== "Frank Herbert") {
    throw new Error("Metadata was not normalized");
  }
});

Deno.test("rejects missing explicit consent", () => {
  let rejected = false;
  try {
    parseCreateUploadInput({
      title: "Dune",
      author: "Frank Herbert",
      language: "en",
      rightsConfirmed: false,
    });
  } catch (error) {
    rejected = error instanceof InputError;
  }
  if (!rejected) throw new Error("Missing consent was accepted");
});

Deno.test("rejects client-supplied owner or storage fields", () => {
  let rejected = false;
  try {
    parseCreateUploadInput({
      title: "Dune",
      author: "Frank Herbert",
      language: "en",
      rightsConfirmed: true,
      owner_id: "attacker-controlled",
    });
  } catch (error) {
    rejected = error instanceof InputError;
  }
  if (!rejected) throw new Error("Unsupported identity field was accepted");
});

Deno.test("rejects oversized and control-character metadata", () => {
  for (const title of ["", "x".repeat(201), "bad\nname"]) {
    let rejected = false;
    try {
      parseCreateUploadInput({
        title,
        author: "Author",
        language: "en",
        rightsConfirmed: true,
      });
    } catch (error) {
      rejected = error instanceof InputError;
    }
    if (!rejected) throw new Error("Invalid title was accepted");
  }
});

Deno.test("rejects malformed language tags", () => {
  let rejected = false;
  try {
    parseCreateUploadInput({
      title: "Title",
      author: "Author",
      language: "english!!",
      rightsConfirmed: true,
    });
  } catch (error) {
    rejected = error instanceof InputError;
  }
  if (!rejected) throw new Error("Malformed language tag was accepted");
});
