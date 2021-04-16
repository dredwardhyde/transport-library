package com.jaffa.rpc.lib.security

import java.io.Serializable

class SecurityTicket(var user: String?, var token: String?) : Serializable {

    constructor() : this(null, null)

}