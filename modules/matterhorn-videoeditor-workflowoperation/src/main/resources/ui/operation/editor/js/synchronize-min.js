/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * Synchronize.js
 * Version 1.1.0
 * 
 * Attention: Version without a pause when buffering!
 */
(function($){var videoIds=[];var videoIdsReady={};var videoIdsInit={};var masterVidNumber=0;var masterVideoId;var nrOfPlayersReady=0;var lastSynch=0;var synchInterval=2000;var synchGap=1.0;var startClicked=false;var bufferCheckerSet=false;var bufferChecker;var checkBufferInterval=1000;var playWhenBuffered=false;var ignoreNextPause=false;var hitPauseWhileBuffering=false;var bufferInterval=1.5;function isInInterval(num,lower,upper){if(!isNaN(num)&&!isNaN(lower)&&!isNaN(upper)&&(lower<=upper)){return((num>=lower)&&(num<=upper));}else{return false;}}
function getVideoObj(id){if(id){if(!useVideoJs()){return $("#"+id);}else{return videojs(id);}}else{return undefined;}}
function getVideo(id){if(id){if(!useVideoJs()){return getVideoObj(id).get(0);}else{return videojs(id);}}else{return undefined;}}
function useVideoJs(){return!(typeof videojs==="undefined");}
function getVideoId(videojsVideo){if(useVideoJs()&&videojsVideo){return videojsVideo.Q;}else{return videojsVideo;}}
function play(id){if(id){getVideo(id).play();return true;}else{return false;}}
function mute(id){if(id){if(!useVideoJs()){getVideo(id).muted=true;}else{getVideo(id).volume(0);}}else{return undefined;}}
function pause(id){if(id){return getVideo(id).pause();}else{return false;}}
function isPaused(id){if(id){if(!useVideoJs()){return getVideo(id).paused;}else{return getVideo(id).paused();}}else{return false;}}
function getDuration(id){if(id){if(!useVideoJs()){return getVideo(id).duration;}else{return getVideo(id).duration();}}else{return-1;}}
function getCurrentTime(id){if(id){if(!useVideoJs()){return getVideo(id).currentTime;}else{return getVideo(id).currentTime();}}else{return-1;}}
function setCurrentTime(id,time){var duration=getDuration(id);if(id&&(duration!=-1)&&!isNaN(time)&&(time>=0)&&(time<=duration)){if(!useVideoJs()){getVideo(id).currentTime=time;}else{getVideo(id).currentTime(time);}
return true;}else{setCurrentTime(id,duration);return false;}}
function getBufferTimeRange(id){if(id){if(!useVideoJs()){return getVideo(id).buffered;}else{return getVideo(id).buffered();}}else{return undefined;}}
function synchronize(){var ct1=getCurrentTime(masterVideoId);var ct2;for(var i=0;i<videoIds.length;++i){if(videoIds[i]!=masterVideoId){ct2=getCurrentTime(videoIds[i]);if((ct1!=-1)&&(ct2!=-1)&&!isInInterval(ct2,ct1-synchGap,ct1)){$(document).trigger("sjs:synchronizing",[ct1,videoIds[i]]);if(!setCurrentTime(videoIds[i],ct1)){}else{play(videoIds[i]);}}}}}
function registerEvents(){if(allVideoIdsInitialized()){var masterPlayer=getVideoObj(masterVideoId);masterPlayer.on("play",function(){$(document).trigger("sjs:masterPlay",[getCurrentTime(masterVideoId)]);hitPauseWhileBuffering=false;if(!bufferCheckerSet){bufferCheckerSet=true;setBufferChecker();}
for(var i=0;i<videoIds.length;++i){if(videoIds[i]!=masterVideoId){getVideoObj(videoIds[i]).on("play",function(){mute(videoIds[i]);});play(videoIds[i]);}}});masterPlayer.on("pause",function(){$(document).trigger("sjs:masterPause",[getCurrentTime(masterVideoId)]);hitPauseWhileBuffering=!ignoreNextPause&&playWhenBuffered&&true;ignoreNextPause=ignoreNextPause?!ignoreNextPause:ignoreNextPause;for(var i=0;i<videoIds.length;++i){if(videoIds[i]!=masterVideoId){pause(videoIds[i]);synchronize();}}});masterPlayer.on("ended",function(){$(document).trigger("sjs:masterEnded",[getDuration(masterVideoId)]);hitPauseWhileBuffering=true;for(var i=0;i<videoIds.length;++i){if(videoIds[i]!=masterVideoId){synchronize();pause(videoIds[i]);}}});masterPlayer.on("timeupdate",function(){$(document).trigger("sjs:masterTimeupdate",[getCurrentTime(masterVideoId)]);hitPauseWhileBuffering=true;var now=Date.now();if(((now-lastSynch)>synchInterval)||isPaused(masterVideoId)){lastSynch=now;var video;var paused;for(var i=0;i<videoIds.length;++i){if(videoIds[i]!=masterVideoId){mute(videoIds[i]);paused=isPaused(videoIds[i]);synchronize();if(paused||isPaused(masterVideoId)){pause(videoIds[i]);}}}}});}else{for(var i=0;i<videoIds.length;++i){pause(videoIds[i]);}}}
function setBufferChecker(){bufferChecker=window.setInterval(function(){var allBuffered=true;var currTime=getCurrentTime(masterVideoId);for(var i=0;i<videoIds.length;++i){var bufferedTimeRange=getBufferTimeRange(videoIds[i]);if(bufferedTimeRange){var duration=getDuration(videoIds[i]);var currTimePlusBuffer=getCurrentTime(videoIds[i])+bufferInterval;var buffered=false;for(j=0;(j<bufferedTimeRange.length)&&!buffered;++j){currTimePlusBuffer=(currTimePlusBuffer>=duration)?duration:currTimePlusBuffer;if(isInInterval(currTimePlusBuffer,bufferedTimeRange.start(j),bufferedTimeRange.end(j))){buffered=true;}}
allBuffered=allBuffered&&buffered;}else{}}
if(!allBuffered){playWhenBuffered=true;ignoreNextPause=true;for(var i=0;i<videoIds.length;++i){}
hitPauseWhileBuffering=false;$(document).trigger("sjs:buffering",[]);}else if(playWhenBuffered&&!hitPauseWhileBuffering){playWhenBuffered=false;play(masterVideoId);hitPauseWhileBuffering=false;$(document).trigger("sjs:bufferedAndAutoplaying",[]);}else if(playWhenBuffered){playWhenBuffered=false;$(document).trigger("sjs:bufferedButNotAutoplaying",[]);}},checkBufferInterval);}
function setMasterVideoId(playerMasterVideoNumber){masterVidNumber=(playerMasterVideoNumber<videoIds.length)?playerMasterVideoNumber:0;masterVideoId=videoIds[masterVidNumber];$(document).trigger("sjs:masterSet",[masterVideoId]);}
function doWhenDataLoaded(id,func){if(id&&func){getVideoObj(id).on("loadeddata",function(){func();});}}
function allVideoIdsInitialized(){if(!useVideoJs()){return(nrOfPlayersReady==videoIds.length);}else{for(var i=0;i<videoIds.length;++i){if(!videoIdsInit[videoIds[i]]){return false;}}
return true;}}
function allVideoIdsReady(){if(!useVideoJs()){return(nrOfPlayersReady==videoIds.length);}else{for(var i=0;i<videoIds.length;++i){if(!videoIdsReady[videoIds[i]]){return false;}}
return true;}}
function initialPlay(){var myPlayer=this;for(var i=0;i<videoIds.length;++i){pause(videoIds[i]);}
startClicked=true;}
function initialPause(){var myPlayer=this;for(var i=0;i<videoIds.length;++i){pause(videoIds[i]);}
startClicked=false;}
$.synchronizeVideos=function(playerMasterVidNumber,videoId1OrMediagroup,videoId2){var validIds=true;if((arguments.length==2)){var videosInMediagroup=$("video[mediagroup=\""+videoId1OrMediagroup+"\"]");for(var i=0;i<videosInMediagroup.length;++i){var l=videoIds.length;videoIds[l]=videosInMediagroup[i].getAttribute("id");var videoJsIdAddition="_html5_api";videoIds[l]=(useVideoJs()&&(videoIds[l].indexOf(videoJsIdAddition)!=-1))?(videoIds[l].substr(0,videoIds[l].length-videoJsIdAddition.length)):videoIds[l];videoIdsReady[videoIds[i-1]]=false;videoIdsInit[videoIds[i-1]]=false;}}else{masterVidNumber=playerMasterVidNumber;for(var i=1;i<arguments.length;++i){validIds=validIds&&arguments[i]&&($("#"+arguments[i]).length);if(!validIds){$(document).trigger("sjs:invalidId",[arguments[i]]);}else{videoIds[videoIds.length]=arguments[i];videoIdsReady[videoIds[i-1]]=false;videoIdsInit[videoIds[i-1]]=false;}}}
if(validIds&&(videoIds.length>1)){if(!useVideoJs()){for(var i=0;i<videoIds.length;++i){$(document).trigger("sjs:idRegistered",[videoIds[i]]);var plMVN=playerMasterVidNumber;getVideoObj(videoIds[i]).on("play",initialPlay);getVideoObj(videoIds[i]).on("pause",initialPause);getVideoObj(videoIds[i]).ready(function(){++nrOfPlayersReady;if(allVideoIdsInitialized()){setMasterVideoId(plMVN);for(var i=0;i<videoIds.length;++i){getVideoObj(videoIds[i]).off("play",initialPlay);getVideoObj(videoIds[i]).off("pause",initialPause);}
registerEvents();if(startClicked){play(masterVideoId);}
$(document).trigger("sjs:allPlayersReady",[]);}});}}else{for(var i=0;i<videoIds.length;++i){$(document).trigger("sjs:idRegistered",[videoIds[i]]);var plMVN=playerMasterVidNumber;getVideoObj(videoIds[i]).on("play",initialPlay);getVideoObj(videoIds[i]).on("pause",initialPause);getVideoObj(videoIds[i]).ready(function(){var playerName=getVideoId(this);videoIdsReady[playerName]=true;doWhenDataLoaded(playerName,function(){videoIdsInit[playerName]=true;$(document).trigger("sjs:playerLoaded",[playerName]);if(allVideoIdsInitialized()){setMasterVideoId(plMVN);for(var i=0;i<videoIds.length;++i){getVideoObj(videoIds[i]).off("play",initialPlay);getVideoObj(videoIds[i]).off("pause",initialPause);}
registerEvents();if(startClicked){play(masterVideoId);}
$(document).trigger("sjs:allPlayersReady",[]);}});});}}}else{$(document).trigger("sjs:notEnoughVideos",[]);}
$(document).on("sjs:play",function(e){if(allVideoIdsInitialized()){play(masterVideoId);}});$(document).on("sjs:pause",function(e){if(allVideoIdsInitialized()){pause(masterVideoId);}});$(document).on("sjs:setCurrentTime",function(e,time){if(allVideoIdsInitialized()){setCurrentTime(masterVideoId,time);}});$(document).on("sjs:synchronize",function(e){if(allVideoIdsInitialized()){synchronize();}});$(document).on("sjs:startBufferChecker",function(e){if(!bufferCheckerSet){window.clearInterval(bufferChecker);bufferCheckerSet=true;setBufferChecker();}});$(document).on("sjs:stopBufferChecker",function(e){window.clearInterval(bufferChecker);bufferCheckerSet=false;});}})(jQuery);
