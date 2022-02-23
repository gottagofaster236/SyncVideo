# SyncVideo

This is an app for Android that enables synchronous (<1 frame of latency) playback of videos according to a schedule.

## Prerequisites
Several devices on the same local area network (preferably by Ethernet), running Android 8 or higher.

## Usage

### Initial setup
- Install and run [the apk](https://github.com/gottagofaster236/SyncVideo/releases/latest).
- Select the device number (1, 2, 3, any number).
- Choose the device type. Among all devices, there has to be exactly one server and several clients.
- Click "Continue". The main app screen will open. If you click the screen anywhere, you'll see an overlay showing the logs and a button going to settings.

### Setting up the server
The server stores the schedule of the videos and the videos themselves. Clients download the needed videos and the schedule. The server can play the videos too, but it will use the files that are already saved locally.

On the server, create a folder where you will store the configuration files. Create a file inside that folder called `schedule.txt`.

`schedule.txt` format is based on JSON, here's an example:

```json
{"timezoneOffset": 3, "scheduledVideos": [
    {"deviceId": "1", "filename": "example file.mp4", "startTime": "04:20", "loop": true},
    {"deviceId": "2", "filename": "some other file.mp4", "startTime": "16:19.59", "loop": false}
]}
```
On device number 1, a looping video is scheduled to start at 4:20. Meanwhile, on device number 2, a non-looping video is scheduled to start at 16:19.59. If the video doesn't loop, it's stopped at the last frame. The schedule repeats every day. In this example, all of the times are provided in GMT+3, as specified by the `timezoneOffset` field.

The videos that `schedule.txt` references should be located in the same folder.

After you setup the configuration folder, open the settings on the server device, and choose the location of that folder.

Now you're good to go! The clients will download the needed videos automatically (the download takes time, obviously) and will start playback when ready. If you want to have pre-downloaded videos on clients, you can create a configuration folder the same way as for the server (but without the schedule file) and select it in the settings.

## Building
Use Android Studio to build the app.

This app was rushed to be finished in a week, so the code quality suffers a little.
