package com.homecursor.client.protocol

/** The peer sent bytes that are not valid protocol data. */
class ProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)
