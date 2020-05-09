/*
 * Copyright (C) 2020 Jeffrey Thomas Piercy
 *
 * This file is part of Deuce-Android.
 *
 * Deuce-Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Deuce-Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Deuce-Android.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.mqduck.deuce.common

import android.util.Log
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.io.BufferedWriter
import java.io.File
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

class MatchList : ArrayList<DeuceMatch> {
    val file: File
    val backupFile: File
    private var writerThread: Thread? = null

    constructor(file: File, backupFile: File) : super() {
        this.file = file
        this.backupFile = backupFile

        if (file.exists()) {
            try {
                addAll((JSONParser().parse(file.reader()) as JSONArray).map { jsonObjectToMatch(it as JSONObject) })
            } catch (e: Exception) {
                Log.d("foo", "Error loading scoreList file: $e")
                if (backupFile.exists()) {
                    try {
                        addAll((JSONParser().parse(backupFile.reader()) as JSONArray)
                            .map { jsonObjectToMatch(it as JSONObject) })
                        backupFile.copyTo(file, true)
                    } catch (f: Exception) {
                        Log.d("foo", "Error loading or copying backup scoreList file: $f")
                    }
                }
            }
        }
    }

    constructor(file: File, backupFile: File, list: List<DeuceMatch>) : super(list) {
        this.file = file
        this.backupFile = backupFile
    }

    private fun jsonObjectToMatch(json: JSONObject): DeuceMatch {
        /*val setsStartTimesList = ArrayList<Long>()
        for (startTime in json[KEY_SETS_START_TIMES] as JSONArray) {
            setsStartTimesList.add(startTime as Long)
        }
        val setsEndTimesList = ArrayList<Long>()
        for (endTime in json[KEY_SETS_END_TIMES] as JSONArray) {
            setsEndTimesList.add(endTime as Long)
        }
        val scoreStackLongArray = ArrayList<Long>()
        for (long in json[KEY_SCORE_ARRAY] as JSONArray) {
            scoreStackLongArray.add(long as Long)
        }*/

        return DeuceMatch(
            NumSets.fromOrdinal((json[KEY_NUM_SETS] as Long).toInt()),
            Team.fromOrdinal((json[KEY_SERVER] as Long).toInt()),
            OvertimeRule.fromOrdinal((json[KEY_OVERTIME_RULE] as Long).toInt()),
            MatchType.fromOrdinal((json[KEY_MATCH_TYPE] as Long).toInt()),
            PlayTimesData(json[KEY_MATCH_START_TIME] as Long, json[KEY_MATCH_END_TIME] as Long),
            PlayTimesList(
                ArrayList((json[KEY_SETS_START_TIMES] as JSONArray).map { it as Long }),
                ArrayList((json[KEY_SETS_END_TIMES] as JSONArray).map { it as Long })
            ),
            ScoreStack(
                (json[KEY_SCORE_SIZE] as Long).toInt(),
                BitSet.valueOf((json[KEY_SCORE_ARRAY] as JSONArray).map { it as Long }.toLongArray())
            ),
            json[KEY_NAME_TEAM1] as String,
            json[KEY_NAME_TEAM2] as String
        )
    }

    private fun matchToJSONObject(match: DeuceMatch): JSONObject {
        val json = JSONObject()

        json[KEY_NUM_SETS] = match.numSets.ordinal
        json[KEY_SERVER] = match.startingServer.ordinal
        json[KEY_OVERTIME_RULE] = match.overtimeRule.ordinal
        json[KEY_MATCH_TYPE] = match.matchType.ordinal
        json[KEY_MATCH_START_TIME] = match.playTimes.startTime
        json[KEY_MATCH_END_TIME] = match.playTimes.endTime
        val setsStartTimesJSON = JSONArray()
        setsStartTimesJSON.addAll(match.setsTimesLog.getStartTimesArray().toList())
        json[KEY_SETS_START_TIMES] = setsStartTimesJSON
        val setsEndTimesJSON = JSONArray()
        setsEndTimesJSON.addAll(match.setsTimesLog.getEndTimesArray().toList())
        json[KEY_SETS_END_TIMES] = setsEndTimesJSON
        json[KEY_SCORE_SIZE] = match.scoreLog.size
        val scoreLogArrayJSON = JSONArray()
        scoreLogArrayJSON.addAll(match.scoreLog.bitSetToLongArray().toList())
        json[KEY_SCORE_ARRAY] = scoreLogArrayJSON
        json[KEY_NAME_TEAM1] = match.nameTeam1
        json[KEY_NAME_TEAM2] = match.nameTeam2

        return json
    }

    fun writeToFile() {
        val matchJSONObjectList = map { matchToJSONObject(it) }
        writerThread?.join()
        writerThread = thread {
            val fileWriter = file.bufferedWriter()
            val json = JSONArray()
            json.addAll(matchJSONObjectList)
            fileWriter.write(json.toString())
            fileWriter.flush()
            fileWriter.close()
            file.copyTo(backupFile, true)
        }
    }

    fun waitForWrite() {
        writerThread?.join()
    }
}