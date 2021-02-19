# Camera Color for Yeelight

<p align="center">
  <img src="example.gif" alt="Example of this app working" />
</p>

The objetive of this Android app is to simulate the Philips Hue Ambilight/Sync Box with Yeelight RGB bulbs, the app uses the device camera to determine the dominant color and change the Yeelight color with a telnet connection to its IP Address.

I recommend to buy a wall mount support for the phone and point the device camera to the television with this app on, it will determine the main color on the TV and send it to the Yeelight bulb IP address you put on the app.

The app also supports more than one bulb IP and crop the image to a selected area for each bulb, it's useful when the wall mount needs to be exactly in front to the tv but people can sit in front of it, so you can put the wall mount a little upper and use the crop to select only the TV on the image, also to change each bulb color to each TV side.

This app was made in less than a day so don't expect a beautiful code and I am rathering to work as a web developer right now. I also don't want to put this app on the Play Store, you can compile it with Android Studio or download the APK on the releases tab.

## Thanks to

* [CameraView](https://github.com/natario1/CameraView/) - amazing library, I used it as the camera on the app.
* [Android-Telnet-Client](https://github.com/hkdsun/Android-Telnet-Client) - base code to the Telnet Client used on this app.
* [Image Cropping Layout](https://github.com/yulu/crop-image-layout) - used to determine color based on a selected image area.
