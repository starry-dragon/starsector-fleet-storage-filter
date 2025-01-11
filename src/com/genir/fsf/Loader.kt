package com.genir.fsf

import java.net.URLClassLoader

class Loader : URLClassLoader((Loader::class.java.classLoader as URLClassLoader).urLs) {
    override fun loadClass(name: String): Class<*> {
        if (name.startsWith("com.genir.fsf")) {
            return super.loadClass(name)
        }

        return try {
            this::class.java.classLoader.loadClass(name)
        } catch (_: SecurityException) {
            super.loadClass(name)
        }
    }
}
