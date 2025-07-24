
# TheSportsDB scraper

tinyMediaManager was designed with movies and TV shows in mind. While there will be similarities to TV shows, there are small, subtle differences for sporting events. For example, a TV show should only have one season/episode combo with same number/date, but in sports, it is common to have multiple events on same day. This is why it is not so easy to handle sports content in TMM - please keep that in mind!

But it does kinda work ;) here are the notes:

First, we begin with the different naming/mappings:

| tinyMediaManager (TMM) | theSportsDB (TSDB) |
| ---------------------- | ------------------ |
|                        | Sport              |
| Show                   | League             |
| Season                 | Season             |
| Episode                | Event              |

When it comes to folder structures, TMM is also quit strict here; The first level of a datasource HAS to be the show folder. Since it is quite common for TSDB user to have a fancy structure like `datasource/sportName/leagueName/seasonName/someFile.mkv` - this does not work out in TMM. You have to add the `sportsName` folder as datasource, so that the first level will be the leagueName.

But then you should be able to search for the league, and get all events for that :)  
(on a simple update, the TSDB leagueId might already be found/preset, if it fits on of those 1000+ known ones)

###Limitations:

* your files should be clearly named, in what season/event they fit in. A date pattern in filename greatly improves matching!
* with the free API key "3", we cannot scrape league details - you'll need an own paid apikey for that.
* with the free API key "3", we only get 100 events per season. Sometimes there are more, which can be fetched with an own paid apikey.
* keep an eye on the (automatic) renamer pattern - it possible won't fit at all! Maybe use a dedicated TMM installation for sports only..?
