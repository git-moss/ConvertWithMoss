# macOS

## Mojave and later

Mojave prevents software to be run which is not authorized by Apple.
But instead of telling you so, you get an error that the files are corrupted
(so, your OS is lying to you now...).

To fix it open the Terminal app and enter the application folder:

```sh
cd /Applications/ConvertWithMoss.app
```

Then remove the evil flag (Requires your administrator password):

```sh
sudo xattr -rc .
```

Since this seems not to work for everybody, there is another solution:

Temporarily, disable the Gatekeeper with

```sh
sudo spctl --master-disable
```

Open the application (should work now). Close it and enable Gatekeeper again to feel safe...

```sh
sudo spctl --master-enable
```

The application should now run also with Gatekeeper enabled.

## macOS 13 Ventura

It got worse on macOS 13 and you need to take another step:

* after you did the xattr thing run it again and click away the error.
* now open the system settings and go to *Privacy & Security Settings*.
* at the very end there should now be a message saying something like
  'publisher of ConvertWithMoss could not identified'.
* Click on the allow anyway button
* when you start ConvertWithMoss again you need to click away another 1000 dialogs but then it works.

Finally, have fun.
