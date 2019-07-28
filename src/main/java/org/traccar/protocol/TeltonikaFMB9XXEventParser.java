/*
 * Copyright 2013 - 2019 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import org.traccar.model.Position;

import java.util.HashMap;

public class TeltonikaFMB9XXEventParser {
    private final HashMap<Integer, String> paramMap;

    public TeltonikaFMB9XXEventParser() {
        // based on https://wiki.teltonika.lt/view/FMB_AVL_ID
        this.paramMap = getParamMap1();
        this.paramMap.putAll(getParamMap2());
    }

    // to avoid style error "Method length is 263 lines (max allowed is 200)."
    // I have split initialization into two parts
    private HashMap<Integer, String> getParamMap1() {
        // based on https://wiki.teltonika.lt/view/FMB_AVL_ID
        return new HashMap<Integer, String>() {{
            put(1, TeltonikaProtocolDecoder.PREFIX_DI + "1");
            put(2, TeltonikaProtocolDecoder.PREFIX_DI + "2");
            put(3, TeltonikaProtocolDecoder.PREFIX_DI + "3");
            put(4, "pulseCounterDin1");
            put(5, "pulseCounterDin2");
            put(6, Position.PREFIX_ADC + "2");
            put(7, "recordsInFlash");
            put(8, "authorizediButton");
            put(9, Position.PREFIX_ADC + "1");
            put(10, "SDStatus");
            put(11, Position.KEY_ICCID + "1");
            put(12, "fuelUsedGPS");
            put(13, "fuelRateGPS");
            put(14, Position.KEY_ICCID + "2");
            put(15, "ecoScore");
            put(16, Position.KEY_ODOMETER);
            put(17, TeltonikaProtocolDecoder.KEY_AXIS_X);
            put(18, TeltonikaProtocolDecoder.KEY_AXIS_Y);
            put(19, TeltonikaProtocolDecoder.KEY_AXIS_Z);
            put(20, "BLE2BatteryVoltage");
            put(21, Position.KEY_RSSI);
            put(22, "BLE3BatteryVoltage");
            put(23, "BLE4BatteryVoltage");
            put(24, Position.KEY_OBD_SPEED);
            put(25, Position.PREFIX_TEMP + "1");
            put(26, Position.PREFIX_TEMP + "2");
            put(27, Position.PREFIX_TEMP + "3");
            put(28, Position.PREFIX_TEMP + "4");
            put(29, "BLE1BatteryVoltage");
            put(30, "numberofDTC");
            put(31, Position.KEY_ENGINE_LOAD);
            put(32, Position.KEY_COOLANT_TEMP);
            put(33, "shortFuelTrim");
            put(34, "fuelPressure");
            put(35, "intakeMAP");
            put(36, Position.KEY_RPM);
            put(37, Position.KEY_OBD_SPEED);
            put(38, "timingAdvance");
            put(39, "intakeAirTemperature");
            put(40, "MAF");
            put(41, "throttlePosition");
            put(42, "runTimeSinceEngineStart");
            put(43, "distanceTraveledMILOn");
            put(44, "relativeFuelRailPressure");
            put(45, "directFuelRailPressure");
            put(46, "commandedEGR");
            put(47, "EGRError");
            put(48, "fuelLevel");
            put(49, "distanceSinceCodesClear");
            put(50, "barometricPressure");
            put(51, "controlModuleVoltage");
            put(52, "absoluteLoadValue");
            put(53, "ambientAirTemperature");
            put(54, "timeRunWithMILOn");
            put(55, "timeSinceCodesCleared");
            put(56, "absoluteFuelRailPressure");
            put(57, "hybridbatterypacklife");
            put(58, "engineOilTemperature");
            put(59, "fuelInjectionTiming");
            put(60, "fuelRate");
            put(61, "geofenceZone06");
            put(62, "geofenceZone07");
            put(63, "geofenceZone08");
            put(64, "geofenceZone09");
            put(65, "geofenceZone10");
            put(66, Position.KEY_POWER);
            put(67, Position.KEY_BATTERY);
            put(68, Position.KEY_BATTERY_CURRENT);
            put(69, Position.KEY_GPS_STATUS);
            put(70, "geofenceZone11");
            put(71, "dallasTemperatureID4");
            put(72, Position.PREFIX_TEMP + "1");
            put(73, Position.PREFIX_TEMP + "2");
            put(74, Position.PREFIX_TEMP + "3");
            put(75, Position.PREFIX_TEMP + "4");
            put(76, "dallasTemperatureID1");
            put(77, "dallasTemperatureID2");
            put(78, Position.KEY_DRIVER_UNIQUE_ID);
            put(79, "dallasTemperatureID3");
            put(80, TeltonikaProtocolDecoder.KEY_WORK_MODE);
            put(81, "vehicleSpeed");
            put(82, Position.KEY_ACCELERATION);
            put(83, "fuelConsumed");
            put(84, "fuelLevel");
            put(85, "engineRPM");
            put(86, "BLE1Humidity");
            put(87, "totalMileage");
            put(88, "geofenceZone12");
            put(89, "fuelLevel");
            put(90, "doorStatus");
            put(91, "geofenceZone13");
            put(92, "geofenceZone14");
            put(93, "geofenceZone15");
            put(94, "geofenceZone16");
            put(95, "geofenceZone17");
            put(96, "geofenceZone18");
            put(97, "geofenceZone19");
            put(98, "geofenceZone20");
            put(99, "geofenceZone21");
            put(100, "programNumber");
            put(101, "moduleID");
            put(102, "engineWorktime");
            put(103, "engineWorktime(counted)");
            put(104, "BLE2Humidity");
            put(105, "totalMileage(counted)");
            put(106, "BLE3Humidity");
            put(107, "fuelConsumed(counted)");
            put(108, "BLE4Humidity");
            put(110, "fuelRate");
            put(111, "adBlueLevel");
            put(112, "adBlueLevel");
            put(113, "batteryLevel");
            put(114, Position.KEY_ENGINE_LOAD);
            put(115, "engineTemperature");
            put(116, "chargerConnected");
            put(117, "drivingDirection");
            put(118, "axle1Load");
            put(119, "axle2Load");
            put(120, "axle3Load");
            put(121, "axle4Load");
            put(122, "axle5Load");
            put(123, "controlStateFlags");
            put(124, "agriculturalMachineryFlags");
            put(125, "harvestingTime");
            put(126, "areaofHarvest");
            put(127, "LVCMowingEfficiency");
            put(128, "grainMownVolume");
            put(129, "grainMoisture");
            put(130, "harvestingDrumRPM");
            put(131, "gapUnderHarvestingDrum");
            put(132, "securityStateFlags");
            put(133, "tachoTotalDistance");
            put(134, "tripDistance");
            put(135, "tachoVehicleSpeed");
            put(136, "tachoDriverCardPresence");
            put(137, "driver1States");
            put(138, "driver2States");
            put(139, "driver1DrivingTime");
            put(140, "driver2DrivingTime");
            put(141, "driver1BreakTime");
            put(142, "driver2BreakTime");
            put(143, "driver1ActivityDuration");
            put(144, "driver2ActivityDuration");
            put(145, "driver1DrivingTime");
            put(146, "driver2DrivingTime");
            put(147, "driver1IDHigh");
            put(148, "driver1IDLow");
            put(149, "driver2IDHigh");
            put(150, "driver2IDLow");
            put(151, "batteryTemperature");
            put(152, "batteryLevel");
            put(153, "geofenceZone22");
            put(154, "geofenceZone23");
            put(155, "geofenceZone01");
            put(156, "geofenceZone02");
            put(157, "geofenceZone03");
            put(158, "geofenceZone04");
            put(159, "geofenceZone05");
        }};
    }
    private HashMap<Integer, String> getParamMap2() {
        // based on https://wiki.teltonika.lt/view/FMB_AVL_ID
        return new HashMap<Integer, String>() {{
            put(160, Position.KEY_DTCS);
            put(161, "slopeOfArm");
            put(162, "rotationOfArm");
            put(163, "ejectOfArm");
            put(164, "horizontalDistanceArm");
            put(165, "heightArmAboveGround");
            put(166, "drillRPM");
            put(167, "spreadSalt");
            put(168, "batteryVoltage");
            put(169, "spreadFineGrainedSalt");
            put(170, "coarseGrainedSalt");
            put(171, "spreadDiMix");
            put(172, "spreadCoarseGrainedCalcium");
            put(173, "spreadCalciumChloride");
            put(174, "spreadSodiumChloride");
            put(175, "autoGeofence");
            put(176, "spreadMagnesiumChloride");
            put(177, "amountOfSpreadGravel");
            put(178, "amountOfSpreadSand");
            put(179, Position.PREFIX_OUT + "1");
            put(180, Position.PREFIX_OUT + "2");
            put(181, Position.KEY_PDOP);
            put(182, Position.KEY_HDOP);
            put(183, "widthPouringLeft");
            put(184, "widthPouringRight");
            put(185, "saltSpreaderWorkingHours");
            put(186, "distanceDuringSalting");
            put(187, "loadWeight");
            put(188, "retarderLoad");
            put(189, "cruiseTime");
            put(190, "geofenceZone24");
            put(191, "geofenceZone25");
            put(192, "geofenceZone26");
            put(193, "geofenceZone27");
            put(194, "geofenceZone28");
            put(195, "geofenceZone29");
            put(196, "geofenceZone30");
            put(197, "geofenceZone31");
            put(198, "geofenceZone32");
            put(199, Position.KEY_ODOMETER_TRIP);
            put(200,  Position.KEY_SLEEP);
            put(201, "LLS1FuelLevel");
            put(202, "LLS1Temperature");
            put(203, "LLS2FuelLevel");
            put(204, "LLS2Temperature");
            put(205, "GSMCellID");
            put(206, "GSMAreaCode");
            put(207, "RFID");
            put(208, "geofenceZone33");
            put(209, "geofenceZone34");
            put(210, "LLS3FuelLevel");
            put(211, "LLS3Temperature");
            put(212, "LLS4FuelLevel");
            put(213, "LLS4Temperature");
            put(214, "LLS5FuelLevel");
            put(215, "LLS5Temperature");
            put(216, "geofenceZone35");
            put(217, "geofenceZone36");
            put(218, "geofenceZone37");
            put(219, "geofenceZone38");
            put(220, "geofenceZone39");
            put(221, "geofenceZone40");
            put(222, "geofenceZone41");
            put(223, "geofenceZone42");
            put(224, "geofenceZone43");
            put(225, "geofenceZone44");
            put(226, "geofenceZone45");
            put(227, "geofenceZone46");
            put(228, "geofenceZone47");
            put(229, "geofenceZone48");
            put(230, "geofenceZone49");
            put(231, "geofenceZone50");
            put(232, "CNGStatus");
            put(233, "CNGUsed");
            put(234, "CNGLevel");
            put(235, "engineOilLevel");
            put(236, "alarm");
            put(237, "networkType");
            put(238, "userID");
            put(239, Position.KEY_IGNITION);
            put(240, Position.KEY_MOTION);
            put(241, Position.KEY_OPERATOR);
            put(242, "manDown");
            put(243, "greenDrivingEventDuration");
            put(244, "DIN2/AIN2SpecEvent");
            put(245, "gyroscopeAxis");
            put(246, "towing");
            put(247, "crash");
            put(248, "immobilizer");
            put(249, "jamming");
            put(250, "trip");
            put(251, "idling");
            put(252, "unplug");
            put(253, "greenDrivingType");
            put(254, "greenDrivingValue");
            put(255, "overSpeeding");
            put(256, Position.KEY_VIN);
            put(257, "crashData");
            put(281, "faultCodes");
            put(303, "instantMovement");
        }};
    }

    public String parseEvent(int id) {
        if (paramMap.containsKey(id)) {
            return paramMap.get(id);
        }

        return null;
    }
}
