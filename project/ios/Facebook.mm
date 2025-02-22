#import <CallbacksDelegate.h>
#import <FacebookObserver.h>
#import <FBSDKCoreKit/FBSDKCoreKit.h>
#import <FBSDKCoreKit/FBSDKSettings.h>
#import <FBSDKCoreKit/FBSDKAppEvents.h>
#import <FBSDKLoginKit/FBSDKLoginKit.h>
#import <FBSDKShareKit/FBSDKAppInviteContent.h>
#import <FBSDKShareKit/FBSDKAppInviteDialog.h>
#import <FBSDKShareKit/FBSDKGameRequestContent.h>
#import <FBSDKShareKit/FBSDKGameRequestDialog.h>
#import <FBSDKShareKit/FBSDKShareDialog.h>
#import <FBSDKShareKit/FBSDKShareLinkContent.h>

#import <Facebook.h>

namespace extension_facebook {

	CallbacksDelegate *callbacks;
	FacebookObserver *obs;
	FBSDKLoginManager *login;
	UIViewController *root;

	void pre_init() {
		login = [[FBSDKLoginManager alloc] init];
		obs = [[FacebookObserver alloc] init];
		[[NSNotificationCenter defaultCenter]
			addObserver:obs
			selector:@selector(applicationDidFinishLaunchingNotification:)
			name:@"UIApplicationDidFinishLaunchingNotification"
			object:nil
		];
	}

	void init() {
		root = [[[UIApplication sharedApplication] keyWindow] rootViewController];
		callbacks = [[CallbacksDelegate alloc] init];
		
		[[FBSDKApplicationDelegate sharedInstance] application:[UIApplication sharedApplication]
									didFinishLaunchingWithOptions:[[NSMutableDictionary alloc] init]];
		
		[obs observeTokenChange:nil];

		[[NSNotificationCenter defaultCenter]
			addObserver:obs
			selector:@selector(observeTokenChange:)
			name:FBSDKAccessTokenDidChangeNotification
			object:nil
		];

	}

	void setDebug() {
		NSLog(@"Facebook: set debug mode");
		[FBSDKSettings enableLoggingBehavior:FBSDKLoggingBehaviorAppEvents];
	}
	
	void logEvent(std::string name, std::string payload) {
		NSLog(@"Facebook: logEvent name= %s, payload= %s", name.c_str(), payload.c_str());
		
        NSString * nsName = [[NSString alloc] initWithUTF8String:name.c_str()];
        NSString * nsPayload = [[NSString alloc] initWithUTF8String:payload.c_str()];
		NSData * jsonData = [nsPayload dataUsingEncoding:NSUTF8StringEncoding];
		NSError * error = nil;
		NSDictionary * params = [NSJSONSerialization JSONObjectWithData:jsonData options:kNilOptions error:&error];
		
		[FBSDKAppEvents logEvent:nsName parameters:params];
	}
	
	void logPurchase(double value, std::string currency, std::string payload) {
		NSLog(@"Facebook: logPurchase val=%f", value);
        NSLog(@"Facebook: logPurchase currency=%s", currency.c_str());
		NSLog(@"Facebook: logPurchase payload=%s", payload.c_str());
		
        NSString * nsCurrency = [[NSString alloc] initWithUTF8String:currency.c_str()];
		
		NSString * nsPayload = [[NSString alloc] initWithUTF8String:payload.c_str()];
		NSData * jsonData = [nsPayload dataUsingEncoding:NSUTF8StringEncoding];
		NSError * error = nil;
		NSDictionary * params = [NSJSONSerialization JSONObjectWithData:jsonData options:kNilOptions error:&error];
		
		[FBSDKAppEvents logPurchase:value currency:nsCurrency parameters:params];
	}
	
	void logOut() {
		[login logOut];
	}

	void logInWithPublishPermissions(std::vector<std::string> &permissions) {
		NSMutableArray *nsPermissions = [[NSMutableArray alloc] init];
		for (auto p : permissions) {
			[nsPermissions addObject:[NSString stringWithUTF8String:p.c_str()]];
		}
		[login logInWithPublishPermissions:nsPermissions fromViewController:root handler:^(FBSDKLoginManagerLoginResult *result, NSError *error) {
			if (error) {
				onLoginErrorCallback([error.localizedDescription UTF8String]);
			} else if (result.isCancelled) {
				onLoginCancelCallback();
			} else {
				onLoginSuccessCallback();
			}
		}];
	}

	void logInWithReadPermissions(std::vector<std::string> &permissions) {
		NSMutableArray *nsPermissions = [[NSMutableArray alloc] init];
		for (auto p : permissions) {
			[nsPermissions addObject:[NSString stringWithUTF8String:p.c_str()]];
		}
		[login logInWithReadPermissions:nsPermissions fromViewController:root handler:^(FBSDKLoginManagerLoginResult *result, NSError *error) {
			if (error) {
				onLoginErrorCallback([error.localizedDescription UTF8String]);
			} else if (result.isCancelled) {
				onLoginCancelCallback();
			} else {
				onLoginSuccessCallback();
			}
		}];
	}

	void appInvite(std::string appLinkUrl, std::string previewImageUrl) {

		FBSDKAppInviteContent *content =[[FBSDKAppInviteContent alloc] init];
		content.appLinkURL = [NSURL URLWithString:[NSString stringWithUTF8String:appLinkUrl.c_str()]];
		if (previewImageUrl!="") {
			content.appInvitePreviewImageURL = [NSURL URLWithString:[NSString stringWithUTF8String:previewImageUrl.c_str()]];
		}

		FBSDKAppInviteDialog *dialog = [[FBSDKAppInviteDialog alloc] init];
		dialog.content = content;
		dialog.delegate = callbacks;
		[dialog show];

	}

	void shareLink(
		std::string contentURL,
		std::string contentTitle,
		std::string imageURL,
		std::string contentDescription) {

		FBSDKShareLinkContent *content = [[FBSDKShareLinkContent alloc] init];
		content.contentURL = [NSURL URLWithString:[NSString stringWithUTF8String:contentURL.c_str()]];
		/*if (contentTitle!="") {
			content.contentTitle = [NSString stringWithUTF8String:contentTitle.c_str()];
		}
		if (imageURL!="") {
			content.imageURL = [NSURL URLWithString:[NSString stringWithUTF8String:imageURL.c_str()]];
		}
		if (contentDescription!="") {
			content.contentDescription = [NSString stringWithUTF8String:contentDescription.c_str()];
		}*/

		int osVersion = [[NSProcessInfo processInfo] operatingSystemVersion].majorVersion;
		FBSDKShareDialog *dialog = [[FBSDKShareDialog alloc] init];
		dialog.fromViewController = root;
		dialog.shareContent = content;
		dialog.delegate = callbacks;
		if (osVersion>=9) {
			dialog.mode = FBSDKShareDialogModeFeedWeb;
		}
		[dialog show];

	}

	void appRequest(
		std::string message,
		std::string title,
		std::vector<std::string> &recipients,
		std::string objectId,
		int actionType,
		std::string data) {

		FBSDKGameRequestContent *gameRequestContent = [[FBSDKGameRequestContent alloc] init];
		gameRequestContent.message = [NSString stringWithUTF8String:message.c_str()];
		gameRequestContent.title = [NSString stringWithUTF8String:title.c_str()];

		NSMutableArray *nsRecipients = [[NSMutableArray alloc] init];
		for (auto p : recipients) {
			[nsRecipients addObject:[NSString stringWithUTF8String:p.c_str()]];
		}
		gameRequestContent.recipients = nsRecipients;

		if (objectId!="") {
			gameRequestContent.objectID = [NSString stringWithUTF8String:objectId.c_str()];
		}

		switch (actionType) {
			case 1:
			gameRequestContent.actionType = FBSDKGameRequestActionTypeSend;
			break;
			case 2:
			gameRequestContent.actionType = FBSDKGameRequestActionTypeAskFor;
			break;
			case 3:
			gameRequestContent.actionType = FBSDKGameRequestActionTypeTurn;
			break;
			default:
			gameRequestContent.actionType = FBSDKGameRequestActionTypeSend;
			break;
		}

		if (data!="") {
			gameRequestContent.data = [NSString stringWithUTF8String:data.c_str()];
		}
		[FBSDKGameRequestDialog showWithContent:gameRequestContent delegate:callbacks];

	}

}
