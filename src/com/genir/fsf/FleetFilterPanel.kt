package com.genir.fsf

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.campaign.fleet.FleetData
import com.fs.starfarer.campaign.fleet.FleetMember
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.lang.reflect.Method

class FleetFilterPanel(width: Float, height: Float, private val fleetPanel: UIPanelAPI) : CustomUIPanelPlugin {
    private val fleetPanelClass: Class<*> = fleetPanel::class.java
    private val getFleetData: Method = fleetPanelClass.methods.first { it.name == "getFleetData" }
    private val recreateUI: Method = fleetPanelClass.methods.first { it.name == "recreateUI" }
    private val fuzzySearchThreshold: Int = 75

    private val stash: MutableList<FleetMember> = mutableListOf()

    private var mainPanel: CustomPanelAPI = Global.getSettings().createCustom(width, height, this)
    private var textField: TextFieldAPI
    private var prevString: String = ""
    private val xPad = 4f
    private val yPad = -25f

    init {
        val tooltip = mainPanel.createUIElement(width, height, false)
        textField = tooltip.addTextField(width, height, Fonts.DEFAULT_SMALL, 0f)

        // Work only for storage or market fleets,
        // which can be recognized by missing faction.
        if (fleetPanel.fleetData.fleet?.faction == null) {
            mainPanel.addUIElement(tooltip).inTL(0f, 0f)
            fleetPanel.addComponent(mainPanel).inTR(xPad, yPad)
        }
    }

    override fun advance(dt: Float) {
        if (textField.text == prevString) {
            return
        }

        // Merge stash and fleetData to recreate vanilla order.
        applyStash()
        val fleetData: FleetData = fleetPanel.fleetData
        fleetData.sort()

        val descriptions = textField.text.split(" ").filter { it != "" }
        if (descriptions.isNotEmpty()) {
            // Move all ships to stash.
            fleetData.members.forEach { fleetMember ->
                stash.add(fleetMember)
            }
            fleetData.clear()

            // Move selected ships from stash to fleetData,
            // in the order of provided descriptions.
            descriptions.forEach { desc ->
                val stashIterator: MutableIterator<FleetMember> = stash.iterator()
                while (stashIterator.hasNext()) {
                    val fleetMember = stashIterator.next()
                    if (fleetMember.matchesDescription(desc)) {
                        fleetData.addFleetMember(fleetMember)
                        stashIterator.remove()
                    }
                }
            }
        }

        // Redraw the fleet panel.
        recreateUI.invoke(fleetPanel, false)
        fleetPanel.addComponent(mainPanel).inTR(xPad, yPad)

        prevString = textField.text
    }

    /** Stashed ships need to be returned to the original fleet
     * once the fleet panel is closed. */
    fun applyStash() {
        val fleetData = fleetPanel.fleetData
        stash.forEach { fleetMember ->
            fleetData.addFleetMember(fleetMember)
        }
        stash.clear()
    }

    private fun FleetMember.matchesDescription(desc: String): Boolean {
        val hullName: String = (this as FleetMemberAPI).hullSpec.hullName.removeSuffix(" (D)").lowercase()
        val loweredDesc: String = desc.lowercase()
        //minimum query length
        if (desc.length < 2) {return false}
        return when {
            FuzzySearch.partialRatio(hullName, loweredDesc) > fuzzySearchThreshold -> true
            isCivilian && "civilian" == loweredDesc -> true
            isCarrier && "carrier" == loweredDesc -> true
            isPhaseShip && "phase" == loweredDesc -> true
            isFrigate && "frigate" == loweredDesc -> true
            isDestroyer && "destroyer" == loweredDesc -> true
            isCruiser && "cruiser" == loweredDesc -> true
            isCapital && "capital" == loweredDesc -> true

            else -> false
        }
    }

    private val UIPanelAPI.fleetData: FleetData
        get() = getFleetData.invoke(this) as FleetData

    override fun positionChanged(position: PositionAPI) = Unit

    override fun renderBelow(alphaMult: Float) = Unit

    override fun render(alphaMult: Float) = Unit

    override fun processInput(events: List<InputEventAPI>) = Unit

    override fun buttonPressed(buttonId: Any) = Unit
}
