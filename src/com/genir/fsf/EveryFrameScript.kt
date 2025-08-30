package com.genir.fsf

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.CampaignState
import com.fs.starfarer.campaign.fleet.FleetData
import com.genir.fsf.ReflectionUtils.getMethod
import lunalib.lunaSettings.LunaSettings
import kotlin.properties.Delegates

import org.apache.log4j.Logger



//it is actually used but through reflection, if it works im not touching it
@Suppress("unused")
class EveryFrameScript : EveryFrameScript {
    //private val getScreenPanel: Method = CampaignState::class.java.getMethod("getScreenPanel")
    //if this returns null something'
    // s really wrong
    private val getScreenPanel: ReflectionUtils.ReflectedMethod = getMethod("getScreenPanel", CampaignState::class.java)!!
    private val uiPanelClass: Class<*> = getScreenPanel.returnType
    private val getChildrenCopy: ReflectionUtils.ReflectedMethod = getMethod("getChildrenCopy", uiPanelClass)!!
    private var fleetPanelClass: Class<*>? = null

    private var filterPanel: FleetFilterPanel? = null
    private var prevFleetPanel: Any? = null

    companion object {
        //so that it's accessible to FleetFilterPanel
        internal var fuzzySearchThreshold: Int by Delegates.notNull<Int>()
    }

    private val logger: Logger = Global.getLogger(this.javaClass)

    override fun advance(dt: Float) {
        val campaignState = Global.getSector().campaignUI
        if (campaignState.currentCoreTab != CoreUITabId.FLEET) {
            updateFilterPanel(null)
            return
        }

        // Attach a new filter to every fleet panel.
        // should not be getting any nulls
        val screenPanel: Any = getScreenPanel.invoke(campaignState)!!
        val fleetPanel = findFleetPanel(screenPanel) as? UIPanelAPI
        if (fleetPanel != prevFleetPanel) {
            updateFilterPanel(fleetPanel)
        }
    }

    // this only runs once when the user enters a given fleet panel
    private fun updateFilterPanel(fleetPanel: UIPanelAPI?) {
        fuzzySearchThreshold = LunaSettings.getInt("fleet-storage-filter", "fsf_fuzzythreshold")!!
        logger.info(fuzzySearchThreshold)
        filterPanel?.stashAndSort()
        filterPanel = fleetPanel?.let { FleetFilterPanel(232f, 20f, it) }
        prevFleetPanel = fleetPanel
    }

    /** Find the currently displayed FleetPanel, if any. Assume
     * there's only one FleetPanel being displayed at a time. */
    private fun findFleetPanel(uiComponent: Any): Any? {
        // There's no easy way to statically find the FleetPanel Class.
        // Here we find it dynamically, when traversing the UI tree.
        // isFleetPanelClass() call is very expensive, so the result is cached.

        // have we found fleetPanelClass yet? if not, is my parameter a FleetPanel?
        if (fleetPanelClass == null && isFleetPanelClass(uiComponent::class.java)) {
            //if so, then we've got our class, save it for future use
            fleetPanelClass = uiComponent::class.java
        }

        // fleetPanelClass may or may not have been found
        return when {
            // check if my parameter is an instance of fleetPanel, if we've found the class
            fleetPanelClass?.isInstance(uiComponent) == true -> {
                return uiComponent
            }

            // this is the case where fleetPanelClass hasn't been found yet and my parameter
            // was not an instance of FleetPanel, but is an instance of UIPanel
            uiPanelClass.isInstance(uiComponent) -> {
                // I will grab all of UIPanel's children, one of which is FleetPanel
                val children = getChildrenCopy.invoke(uiComponent) as List<*>
                // Put them in sequence and map each to whether it's FleetPanel
                // In effect, our list now has one non-null entry, which is the FleetPanel class
                // as all others will have been nulled by the recursive call.
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
