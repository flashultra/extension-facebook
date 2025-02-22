# extension-facebook
Haxe OpenFL extension for Facebook, supports Facebook SDK for Android and iOS. On other platforms uses Facebook REST API and logins using a Webview.

###How to Install

To install this library, you can simply get the library from haxelib like this:
```bash
haxelib install extension-facebook
```

Once this is done, you just need to add this to your project.xml
```xml
<haxelib name="extension-facebook" />
```


##Usage example

Login the user to Facebook if needed:
```Haxe
import extension.facebook.Facebook;

var facebook:Facebook = Facebook.getInstance();
facebook.init(function(value:Bool){trace("Init Callback");},<YOUR_FACEBOOK_ID>);
if (facebook.accessToken!="") { // Only login if the user is not already logged in
  onLoggedIn();
} else {
  facebook.login(               // Show login dialog
    PermissionsType.Read,
    ["email", "user_likes"],
    onLoggedIn,
    onCancel,
    onError
  );
}
```

Custom events:
```Haxe
extension.facebook.Facebook.getInstance().setUserID(userID:String);
extension.facebook.Facebook.getInstance().logEvent(eventName:String, jsonPayload:String);
extension.facebook.Facebook.getInstance().trackPurchase(purchaseAmount:Float, currency:String, ?parameters:String)
```

Send app invite:
```Haxe
// See https://developers.facebook.com/docs/applinks
extension.facebook.AppInvite.invite("https://fb.me/1654475341456363");
```

Share a link on the users timeline:
```Haxe
extension.facebook.Share.link(
  "<a link to something>",
  "<title>",
  "<link to an image>",
  "<description>"
);
```

Graph API:
```Haxe
facebook.get(
  "/me/permissions",  // Graph API endpoint
  onSuccess,  // Dynamic->Void
  onError     // Dynamic->Void
);
```

##License

The MIT License (MIT) - [LICENSE.md](LICENSE.md)

Copyright &copy; 2012 SempaiGames (http://www.sempaigames.com)

Authors: Daniel Uranga, Joaquín Bengochea & Federico Bricker
