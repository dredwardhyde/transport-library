package com.jaffa.rpc.lib.entities

import java.io.Serializable

class ExceptionHolder(toString: String?) : Serializable {
    val stackTrace: String? = toString

    constructor() : this(null)
}