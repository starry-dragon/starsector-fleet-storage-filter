package com.genir.fsf

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

class BaseModPlugin : BaseModPlugin() {
    private val efs = Loader().loadClass("com.genir.fsf.EveryFrameScript")

    override fun onNewGame() {
        onGameStart()
    }

    override fun onGameLoad(newGame: Boolean) {
        onGameStart()
    }

    private fun onGameStart() {
        val sector = Global.getSector()
        if (!sector.hasTransientScript(efs)) {
            sector.addTransientScript(ReflectionUtils.instantiate(efs) as EveryFrameScript)
        }
    }
}
