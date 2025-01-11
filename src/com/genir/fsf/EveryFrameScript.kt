package com.genir.fsf

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.CampaignState
import com.fs.starfarer.campaign.fleet.FleetData
import java.lang.reflect.Method

class EveryFrameScript : EveryFrameScript {
    private val getScreenPanel: Method = CampaignState::class.java.getMethod("getScreenPanel")
    private val uiPanelClass: Class<*> = getScreenPanel.returnType
    private val getChildrenCopy: Method = uiPanelClass.getMethod("getChildrenCopy")
    private var fleetPanelClass: Class<*>? = null

    private var filterPanel: FleetFilterPanel? = null
    private var prevFleetPanel: Any? = null

    override fun advance(dt: Float) {
        val campaignState = Global.getSector().campaignUI
        if (campaignState.currentCoreTab != CoreUITabId.FLEET) {
            updateFilterPanel(null)
            return
        }

        // Attach a new filter to every fleet panel.
        val screenPanel: Any = getScreenPanel.invoke(campaignState)
        val fleetPanel = findFleetPanel(screenPanel) as? UIPanelAPI
        if (fleetPanel != prevFleetPanel) {
            updateFilterPanel(fleetPanel)
        }
    }

    private fun updateFilterPanel(fleetPanel: UIPanelAPI?) {
        filterPanel?.applyStash()
        filterPanel = fleetPanel?.let { FleetFilterPanel(200f, 20f, it) }
        prevFleetPanel = fleetPanel
    }

    /** Find the currently displayed FleetPanel, if any. Assume
     * there's only one FleetPanel being displayed at a time. */
    private fun findFleetPanel(uiComponent: Any): Any? {
        // There's no easy way to statically find the FleetPanel Class.
        // Here we find it dynamically, when traversing the UI tree.
        // isFleetPanelClass() call is very expensive, so the result is cached.
        if (fleetPanelClass == null && isFleetPanelClass(uiComponent::class.java)) {
            fleetPanelClass = uiComponent::class.java
        }

        return when {
            fleetPanelClass?.isInstance(uiComponent) == true -> {
                return uiComponent
            }

            uiPanelClass.isInstance(uiComponent) -> {
                val children = getChildrenCopy.invoke(uiComponent) as List<*>
                val fleetPanels = children.asSequence().map { child -> findFleetPanel(child!!) }
                fleetPanels.filterNotNull().firstOrNull()
            }

            else -> null
        }
    }

    private fun isFleetPanelClass(clazz: Class<*>): Boolean {
        return clazz.methods.any { it.name == "getOther" && it.returnType == FleetData::class.java }
    }

    override fun isDone(): Boolean = false

    override fun runWhilePaused(): Boolean = true
}
