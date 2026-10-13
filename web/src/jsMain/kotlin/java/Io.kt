@file:Suppress("unused", "PackageDirectoryMismatch")

package java.io

class InputStream(private val bytes: ByteArray) : AutoCloseable {
    fun readBytes(): ByteArray = bytes
    fun bufferedReader(): Reader = Reader(bytes.decodeToString())
    fun reader(): Reader = bufferedReader()
    override fun close() {}
}

class Reader(private val text: String) : AutoCloseable {
    fun readText(): String = text
    fun readLines(): List<String> = text.lines()
    override fun close() {}
}

open class IOException(message: String? = null) : Exception(message)
