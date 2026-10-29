package com.example.oryn.reader.data.local.dao;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.example.oryn.reader.data.local.entity.AnnotationEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AnnotationDao_Impl implements AnnotationDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<AnnotationEntity> __insertionAdapterOfAnnotationEntity;

  private final EntityDeletionOrUpdateAdapter<AnnotationEntity> __deletionAdapterOfAnnotationEntity;

  private final EntityDeletionOrUpdateAdapter<AnnotationEntity> __updateAdapterOfAnnotationEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteForBook;

  public AnnotationDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfAnnotationEntity = new EntityInsertionAdapter<AnnotationEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `annotations` (`id`,`bookId`,`startOffset`,`endOffset`,`kind`,`color`,`note`,`quote`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AnnotationEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getBookId());
        statement.bindLong(3, entity.getStartOffset());
        statement.bindLong(4, entity.getEndOffset());
        statement.bindString(5, entity.getKind());
        statement.bindLong(6, entity.getColor());
        if (entity.getNote() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getNote());
        }
        statement.bindString(8, entity.getQuote());
        statement.bindLong(9, entity.getCreatedAt());
      }
    };
    this.__deletionAdapterOfAnnotationEntity = new EntityDeletionOrUpdateAdapter<AnnotationEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `annotations` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AnnotationEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfAnnotationEntity = new EntityDeletionOrUpdateAdapter<AnnotationEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `annotations` SET `id` = ?,`bookId` = ?,`startOffset` = ?,`endOffset` = ?,`kind` = ?,`color` = ?,`note` = ?,`quote` = ?,`createdAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AnnotationEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getBookId());
        statement.bindLong(3, entity.getStartOffset());
        statement.bindLong(4, entity.getEndOffset());
        statement.bindString(5, entity.getKind());
        statement.bindLong(6, entity.getColor());
        if (entity.getNote() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getNote());
        }
        statement.bindString(8, entity.getQuote());
        statement.bindLong(9, entity.getCreatedAt());
        statement.bindLong(10, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteForBook = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM annotations WHERE bookId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final AnnotationEntity annotation,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfAnnotationEntity.insertAndReturnId(annotation);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final AnnotationEntity annotation,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfAnnotationEntity.handle(annotation);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final AnnotationEntity annotation,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfAnnotationEntity.handle(annotation);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteForBook(final long bookId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteForBook.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, bookId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteForBook.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<AnnotationEntity>> observe(final long bookId) {
    final String _sql = "SELECT * FROM annotations WHERE bookId = ? ORDER BY startOffset";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, bookId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"annotations"}, new Callable<List<AnnotationEntity>>() {
      @Override
      @NonNull
      public List<AnnotationEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBookId = CursorUtil.getColumnIndexOrThrow(_cursor, "bookId");
          final int _cursorIndexOfStartOffset = CursorUtil.getColumnIndexOrThrow(_cursor, "startOffset");
          final int _cursorIndexOfEndOffset = CursorUtil.getColumnIndexOrThrow(_cursor, "endOffset");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
          final int _cursorIndexOfQuote = CursorUtil.getColumnIndexOrThrow(_cursor, "quote");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<AnnotationEntity> _result = new ArrayList<AnnotationEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AnnotationEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpBookId;
            _tmpBookId = _cursor.getLong(_cursorIndexOfBookId);
            final int _tmpStartOffset;
            _tmpStartOffset = _cursor.getInt(_cursorIndexOfStartOffset);
            final int _tmpEndOffset;
            _tmpEndOffset = _cursor.getInt(_cursorIndexOfEndOffset);
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final int _tmpColor;
            _tmpColor = _cursor.getInt(_cursorIndexOfColor);
            final String _tmpNote;
            if (_cursor.isNull(_cursorIndexOfNote)) {
              _tmpNote = null;
            } else {
              _tmpNote = _cursor.getString(_cursorIndexOfNote);
            }
            final String _tmpQuote;
            _tmpQuote = _cursor.getString(_cursorIndexOfQuote);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new AnnotationEntity(_tmpId,_tmpBookId,_tmpStartOffset,_tmpEndOffset,_tmpKind,_tmpColor,_tmpNote,_tmpQuote,_tmpCreatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
