# macOS

Since the build depends on the GitHub build infrastructure, you need a quite recent macOS version.
This is currently macOS 13 for Intel and macOS 14 for ARM based Macs. It might work down to macOS 11 but I cannot test that.

## macOS installation security issues

Macos will complain about different things when you try to run the application:

1. The application is unsafe to run since it is downloaded from the internet
2. The application is unsafe to run since it is not signed by Apple

### Fixing 1

Note: You might also get alternatively a totally confusing error that the application files are corrupted. This is the same issue.

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

### Fixing 2 

* Run the application again and click away the error.
* Now open the system settings and go to *Privacy & Security Settings*.
* At the very end, there should now be a message saying something like
  'publisher of ConvertWithMoss could not be identified'.
* Click on the allow anyway button
* When you start ConvertWithMoss again you need to click away another 1000 dialogs but then it works.

Finally, have fun.
