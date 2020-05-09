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

package net.mqduck.deuce

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.*
import kotlinx.android.synthetic.main.activity_main.*
import net.mqduck.deuce.common.*
import java.io.File
import java.util.*


lateinit var mainActivity: MainActivity

class MainActivity : AppCompatActivity(), DataClient.OnDataChangedListener,
    ScoresListFragment.OnMatchInteractionListener {
    private lateinit var scoresListFragment: ScoresListFragment
    internal lateinit var matchList: MatchList
    internal lateinit var dataClient: DataClient

    init {
        mainActivity = this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        matchList = MatchList(File(filesDir, MATCH_LIST_FILE_NAME))

        // TODO: Remove after testing
        if (BuildConfig.DEBUG && matchList.isEmpty()) {
            val scoreLog = ScoreStack()
            for (i in 0 until 48) {
                scoreLog.push(Team.TEAM1)
            }
            matchList.add(
                DeuceMatch(
                    NumSets.THREE,
                    Team.TEAM1,
                    OvertimeRule.TIEBREAK,
                    MatchType.SINGLES,
                    PlayTimesData(416846345451, 416847346451),
                    PlayTimesList(
                        longArrayOf(0, 0, 0),
                        longArrayOf(0, 0, 0)
                    ),
                    scoreLog,
                    "Myself",
                    "Opponent"
                )
            )
            matchList.add(DeuceMatch())
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Game.init(this)

        scoresListFragment = fragment_scores as ScoresListFragment
        dataClient = Wearable.getDataClient(this)
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        sendSignal(dataClient, PATH_REQUEST_MATCHES_SIGNAL, true)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d("foo", "outside")

        // TODO: Change to local inline function when support is added to Kotlin
        fun syncCurrentMatch(dataMap: DataMap) {
            when (MatchState.fromOrdinal(dataMap.getInt(KEY_MATCH_STATE))) {
                MatchState.NEW -> {
                    Log.d("foo", "adding new match")
                    val newMatch = DeuceMatch(
                        NumSets.fromOrdinal(dataMap.getInt(KEY_NUM_SETS)),
                        Team.fromOrdinal(dataMap.getInt(KEY_SERVER)),
                        OvertimeRule.fromOrdinal(dataMap.getInt(KEY_OVERTIME_RULE)),
                        MatchType.fromOrdinal(dataMap.getInt(KEY_MATCH_TYPE)),
                        PlayTimesData(
                            dataMap.getLong(KEY_MATCH_START_TIME),
                            dataMap.getLong(KEY_MATCH_END_TIME)
                        ),
                        PlayTimesList(
                            dataMap.getLongArray(KEY_SETS_START_TIMES),
                            dataMap.getLongArray(KEY_SETS_END_TIMES)
                        ),
                        ScoreStack(
                            dataMap.getInt(KEY_SCORE_SIZE),
                            BitSet.valueOf(dataMap.getLongArray(KEY_SCORE_ARRAY))
                        ),
                        dataMap.getString(KEY_NAME_TEAM1),
                        dataMap.getString(KEY_NAME_TEAM2)
                    )

                    if (
                        matchList.isNotEmpty() &&
                        matchList.last().winner == Winner.NONE
                    ) {
                        matchList[matchList.lastIndex] = newMatch
                    } else {
                        matchList.add(newMatch)
                    }

                    scoresListFragment.view.adapter?.notifyDataSetChanged()
                }
                MatchState.ONGOING -> {
                    Log.d("foo", "updating current match")
                    if (matchList.isEmpty()) {
                        // TODO: request match information?
                        Log.d("foo", "tried to update current match but no current match exists")
                        return
                    }

                    val currentMatch = matchList.last()
                    currentMatch.playTimes.endTime = dataMap.getLong(KEY_MATCH_END_TIME)
                    currentMatch.setsTimesLog = PlayTimesList(
                        dataMap.getLongArray(KEY_SETS_START_TIMES),
                        dataMap.getLongArray(KEY_SETS_END_TIMES)
                    )
                    currentMatch.scoreLog = ScoreStack(
                        dataMap.getInt(KEY_SCORE_SIZE),
                        BitSet.valueOf(dataMap.getLongArray(KEY_SCORE_ARRAY))
                    )
                }
                MatchState.OVER -> {
                    Log.d("foo", "removing current match")
                    // TODO
                }
            }

            scoresListFragment.view.adapter?.notifyDataSetChanged()
        }

        // TODO: Change to local inline function when support is added to Kotlin
        fun syncFinishedMatches(dataMap: DataMap) {
            if (dataMap.getBoolean(KEY_MATCH_LIST_STATE, false)) {
                val dataMapArray = dataMap.getDataMapArrayList(KEY_MATCH_LIST)
                if (dataMapArray != null) {
                    val currentMatch = if (dataMap.getBoolean(KEY_DELETE_CURRENT_MATCH)) {
                        if (matchList.isNotEmpty() && matchList.last().winner == Winner.NONE) {
                            matchList.removeAt(matchList.lastIndex)
                        }
                        null
                    } else {
                        if (matchList.isNotEmpty() && matchList.last().winner == Winner.NONE)
                            matchList.removeAt(matchList.lastIndex)
                        else
                            null
                    }

                    val matchSet = matchList.toMutableSet()
                    matchSet.addAll(dataMap.getDataMapArrayList(KEY_MATCH_LIST).map { matchDataMap ->
                        DeuceMatch(
                            NumSets.fromOrdinal(matchDataMap.getInt(KEY_NUM_SETS)),
                            Team.fromOrdinal(matchDataMap.getInt(KEY_SERVER)),
                            OvertimeRule.fromOrdinal(matchDataMap.getInt(KEY_OVERTIME_RULE)),
                            MatchType.fromOrdinal(matchDataMap.getInt(KEY_MATCH_TYPE)),
                            PlayTimesData(
                                matchDataMap.getLong(KEY_MATCH_START_TIME),
                                matchDataMap.getLong(KEY_MATCH_END_TIME)
                            ),
                            PlayTimesList(
                                matchDataMap.getLongArray(KEY_SETS_START_TIMES),
                                matchDataMap.getLongArray(KEY_SETS_END_TIMES)
                            ),
                            ScoreStack(
                                matchDataMap.getInt(KEY_SCORE_SIZE),
                                BitSet.valueOf(matchDataMap.getLongArray(KEY_SCORE_ARRAY))
                            ),
                            matchDataMap.getString(KEY_NAME_TEAM1),
                            matchDataMap.getString(KEY_NAME_TEAM2)
                        )
                    })
                    matchList = MatchList(matchList.file, matchSet.sorted())

                    if (currentMatch != null) {
                        matchList.add(currentMatch)
                    }

                    matchList.writeToFile()

                    scoresListFragment.view.adapter?.notifyDataSetChanged()

                    sendSignal(dataClient, PATH_TRANSMISSION_SIGNAL, true)
                } else {
                    Log.d("foo", "dataMapArray is null for some reason")
                }
            }
        }

        dataEvents.forEach { event ->
            // DataItem changed
            if (event.type == DataEvent.TYPE_CHANGED) {
                event.dataItem.also { item ->
                    if (item.uri.path?.compareTo(PATH_CURRENT_MATCH) == 0) {
                        syncCurrentMatch(DataMapItem.fromDataItem(item).dataMap)
                    } else if (item.uri.path?.compareTo(PATH_MATCH_LIST) == 0) {
                        syncFinishedMatches(DataMapItem.fromDataItem(item).dataMap)
                    }
                }
            } /*else if (event.type == DataEvent.TYPE_DELETED) {
                // DataItem deleted
            }*/
        }
    }

    override fun onMatchInteraction(item: Match, position: Int) {
        scoresListFragment.fragmentManager?.let { fragmentManager ->
            val infoDialog = InfoDialog(item, position, scoresListFragment)
            infoDialog.show(fragmentManager, "info")
        }

    }
}
