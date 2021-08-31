Minimal Viable Player
=====================

Lives in the taskbar and plays streaming audio.

<img src="mvp.jpg" width="600">


Features
--------
* Supports MP3, AAC, FLAC, Opus and HLS streams.
    * Powered by [BASS audio library](http://www.un4seen.com/bass.html).
* InstaPause - if the option is enabled, player stops playback when default output device changes or continues
using new default output device otherwise.
* Playlist and settings are persisted between player restarts.


Controls
--------
* Show or Hide playlist - do right mouse click on the player's icon(s).
* Add a new track or Open an .m3u playlist - use the main menu.
* Edit a track or Delete a track - select a track in the playlist and click corresponding icon.
* Play a track - select a track in the playlist and do left mouse click on the player's icon or click the Play icon.
* Stop player - do left mouse click on the player's icon.
* Play next or previous tracks - while a track is playing, do left mouse click on the player's icons.
    * By default, _Next_ and _Previous_ buttons are hidden and can be made visible via the main menu.
* _Play_/_Pause_, _Next_ and _Previous_ media keys are supported.
    * On macOS, to prevent iTunes / Music app from launching when _Play_/_Pause_ media key is pressed,
    hold any of _Command_, _Control_ or _Option_ keys.
* Enable or Disable InstaPause - use the main menu.


Build and run
-------------
```shell
mvn javafx:run
```
