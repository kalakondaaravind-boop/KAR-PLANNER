package androidx.room

/*
 * Desktop stand-ins for the few Room annotations and helpers used by the shared data layer.
 * On the desktop, data is kept in memory and saved to a JSON file (see DesktopDatabase),
 * so these annotations carry no behaviour.
 */

@Target(AnnotationTarget.CLASS)
annotation class Entity(
    val tableName: String = "",
    val indices: Array<Index> = [],
)

@Target(AnnotationTarget.CLASS)
annotation class Index(vararg val value: String, val unique: Boolean = false)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class PrimaryKey

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class ColumnInfo(val name: String = "")

@Target(AnnotationTarget.CLASS)
annotation class Dao

@Target(AnnotationTarget.FUNCTION)
annotation class Query(val value: String)

@Target(AnnotationTarget.FUNCTION)
annotation class Upsert

abstract class RoomDatabase

/** Runs [block]; the desktop store is single-user and saves after every change. */
suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R = block()
