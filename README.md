FCD2pgSQL
================
FCD2pgSQL is a small Java tool to map match Floating Car Data (FCD) and import it as Linestring ZM (x, y, speed, time) to PostGIS.

Setup
-------------
As of v0.1, FCD2pgSQL reads raw FCD already imported a PostgreSQL database and not directly from GPX or GPS files.
If you want to use the tool straight away you need to import your data to PostgreSQL first. This is the relevant part of the schema we are using:

Table: FLOATING_CAR_DATA

| gps_time  | car_id  | longitude | latitude  | speed |
| --------- |:-------:|:---------:|:---------:|:-----:|
|           |         |           |           |       |


Execute
-------------
Build FCD2pgSQL with Maven and run the jar file with the following arguments:

1. host address of Postgres server
2. port of Postgres server
3. database name
4. user name
5. user password
6. path to OSM file
7. path for Graphhopper files (just an empty directory where Graphhopper creates the routing graph)


How it works
-------------
### 1. Read from DB

For each FCD sender (car id) FCD2pgSQL fetches a list of all recorded GPS points. 
It connects multiple points to a GPX track as long as the time and distance between two point does not exceed 1 minute and 1 kilometer.

### 2. Map Matching using Graphhopper's MapMatching API

When a track is complete we send it to the map matcher class where it is matched against the Graphhopper routing graph.
Note, that Graphhopper relies on OpenStreetMap, but the resulting tracks are not 100% identical to the OSM roads.

### 3. Copy timestamps and speed values

Up till now, Graphhopper's Map Matching algorithm does not return the time at the resulting points. So we tried to build a workaround for this.
First, we try to find points in the matched track that come very close (> 50m) to the points of the original GPS track.
This is not trivial, as for each point there can be multiple matching candidates. We are choosing the one with minimum distance.
We then copy the timestamp and the speed value to the list of matched points.

Still, the matched tracks can have gaps with no time and speed. To fill the gaps we are interpolating the speed linearly between points that have a measure.
With the speed value we can calclulate the time at each point (2s/V). When there is a gap at the start or end of the track we simply copy the speed value unless the gap is not bigger than 200m.

### 4. Importing the matched track

We import th track as a PostGIS linestring with speed as the Z value and time as the M. The target Postgres database needs to have the PostGIS extension installed.
FCD2pgSQL creates the target table (and sequence) on the first run. Therefore, the selected user role need to have sufficient database privileges.
The target table has the following schema:

| id  | start_time | end_time | geom |
| --- |:----------:|:--------:|:----:|
|     |            |          |      |

### 5. Working with the data

The resulting linestrings allow for some intersting queries using linear referecing methods.
More explanation coming soon.


Changelog
-------------
### v0.1

* reads FCD from Postgres
* performs MapMatching using Graphhopper
* interpolates time and speed
* writes results to PostGIS as LineString ZM


Future Plans
-------------
* Direct import from CSV,GPX
* Include parallelization


Developers
-------------
Felix Kunde
Manal Faraj
Stephan Pieper


Contact
----------
fkunde@beuth-hochschule.de
spieper@beuth-hochschule.de
sauer@beuth-hochschule.de


Special Thanks
-----------------
* Graphhopper for their MapMatching API and support
* TU Dresden for FCD test data


Acknowledgement
-----------------
FCD2pgSQL has been realized within the ExCELL project funded by the Federal Ministry for Economic Affairs and Energy (BMWi) and German Aerospace Center (DLR) - agreement 01MD15001B.