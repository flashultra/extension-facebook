package org.haxe.extension.facebook;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import com.facebook.LoggingBehavior;
import com.facebook.AccessToken;
import com.facebook.AccessTokenTracker;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookRequestError;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.HttpMethod;
import com.facebook.internal.BundleJSONConverter;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.share.model.AppInviteContent;
import com.facebook.share.model.GameRequestContent.ActionType;
import com.facebook.share.model.GameRequestContent;
import com.facebook.share.model.ShareLinkContent;
import com.facebook.share.widget.AppInviteDialog;
import com.facebook.share.widget.GameRequestDialog.Result;
import com.facebook.share.widget.GameRequestDialog;
import com.facebook.share.widget.ShareDialog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import java.security.MessageDigest;
import android.util.Base64;
import android.content.pm.PackageManager.NameNotFoundException;
import java.security.NoSuchAlgorithmException;

import org.haxe.extension.Extension;
import org.haxe.lime.HaxeObject;

import java.math.BigDecimal;
import java.util.Currency;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Map;

public class FacebookExtension extends Extension {

	static AccessTokenTracker accessTokenTracker;
	static CallbackManager callbackManager;
	static GameRequestDialog requestDialog;
	static SecureHaxeObject callbacks;
	static ShareDialog shareDialog;
	static AppEventsLogger logger;
	static final String TAG = "FACEBOOK-EXTENSION";
	static String APP_ID = "";

	public FacebookExtension() {
	}
	
	private static void servicesInit() {
		FacebookSdk.setApplicationId(APP_ID);
		FacebookSdk.sdkInitialize(mainContext);
		requestDialog = new GameRequestDialog(mainActivity);
		shareDialog = new ShareDialog(mainActivity);

		if (callbackManager!=null) {
			return;
		}

		callbackManager = CallbackManager.Factory.create();
		
		logger = AppEventsLogger.newLogger(mainActivity);

		LoginManager.getInstance().registerCallback(callbackManager,

			new FacebookCallback<LoginResult>() {
				@Override
				public void onSuccess(LoginResult loginResult) {
					if (callbacks!=null) {
						callbacks.call0("_onLoginSucess");
					}
				}

				@Override
				public void onCancel() {
					if (callbacks!=null) {
						callbacks.call0("_onLoginCancel");
					}
				}

				@Override
				public void onError(FacebookException exception) {
					if (callbacks!=null) {
						callbacks.call1("_onLoginError", exception.toString());
					}
				}
		});

		requestDialog.registerCallback(callbackManager, new FacebookCallback<GameRequestDialog.Result>() {

			@Override
			public void onSuccess(Result result) {
				if (callbacks!=null) {
					JSONObject json = new JSONObject();
					try {
						json.put("id", result.getRequestId());
					} catch (JSONException e) {
						Log.d(TAG, "JSONException: " + e.toString());
					}
					JSONArray recipients = new JSONArray(result.getRequestRecipients());
					try {
						json.put("recipients", recipients);
					} catch (JSONException e) {
						Log.d(TAG, "JSONException: " + e.toString());
					}
					callbacks.call1("_onAppRequestComplete", json.toString());
				}
			}

			@Override
			public void onCancel() {
				if (callbacks!=null) {
					callbacks.call1("_onAppRequestFail", "{\"error\" : \"cancelled}\"");
				}
			}

			@Override
			public void onError(FacebookException error) {
				if (callbacks!=null) {
					callbacks.call1("_onAppRequestFail", error.toString());
				}
			}

		});

		shareDialog.registerCallback(callbackManager, new FacebookCallback<ShareDialog.Result>() {

			@Override
			public void onSuccess(ShareDialog.Result result) {
				if (callbacks!=null) {
					JSONObject json = new JSONObject();
					try {
						json.put("postId", result.getPostId());
					} catch (JSONException e) {
						Log.d(TAG, "JSONException" + e.toString());
					}
					callbacks.call1("_onShareComplete", json.toString());
				}
			}

			@Override
			public void onCancel() {
				if (callbacks!=null) {
					callbacks.call1("_onShareFail", "{\"error\" : \"cancelled}\"");
				}
			}

			@Override
			public void onError(FacebookException error) {
				if (callbacks!=null) {
					callbacks.call1("_onShareFail", error.toString());
				}
			}

		});

	}

	public static Object wrap(Object o) {
		if (o == null) {
			return null;
		}
		if (o instanceof JSONArray || o instanceof JSONObject) {
			return o;
		}
		if (o.equals(null)) {
			return o;
		}
		try {
			if (o instanceof Collection) {
				return new JSONArray((Collection) o);
			} else if (o.getClass().isArray()) {
				JSONArray arr = new JSONArray();
				for (Object e : (Object[]) o) {
					arr.put(e);
				}
				return arr;
			}
			if (o instanceof Map) {
				return new JSONObject((Map) o);
			}
			if (o instanceof Boolean ||
				o instanceof Byte ||
				o instanceof Character ||
				o instanceof Double ||
				o instanceof Float ||
				o instanceof Integer ||
				o instanceof Long ||
				o instanceof Short ||
				o instanceof String) {
				return o;
			}
			if (o.getClass().getPackage().getName().startsWith("java.")) {
				return o.toString();
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	// Static methods interface

	public static void init(HaxeObject _callbacks, String appID) {
		
		APP_ID = appID;
		
		callbacks = new SecureHaxeObject(_callbacks, mainActivity, TAG);
		
		try {
			servicesInit();

			accessTokenTracker = new AccessTokenTracker() {
				@Override
				protected void onCurrentAccessTokenChanged(AccessToken oldAccessToken, AccessToken currentAccessToken) {
					if (callbacks!=null) {
						if (currentAccessToken!=null) {
							callbacks.call1("_onTokenChange", currentAccessToken.getToken());
						} else {
							callbacks.call1("_onTokenChange", "");
						}
					}
				}
			};

			mainActivity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					try 
					{
						AccessToken token = AccessToken.getCurrentAccessToken();
						if (token!=null) {
							callbacks.call1("_onTokenChange", token.getToken());
						} else {
							callbacks.call1("_onTokenChange", "");
						}
					} catch (ExceptionInInitializerError error) {
						callbacks.call1("_onTokenChange", "");
					}
				}
			});
			
		} catch (ExceptionInInitializerError error) {
			callbacks.call1("_onTokenChange", "");
		}

	}

	public static void logout() {
		LoginManager.getInstance().logOut();
	}

	public static void logInWithPublishPermissions(String permissions) {
		String[] arr = permissions.split(";");
		LoginManager.getInstance().logInWithPublishPermissions(mainActivity, Arrays.asList(arr));
	}

	public static void logInWithReadPermissions(String permissions) {
		String[] arr = permissions.split(";");
		LoginManager.getInstance().logInWithReadPermissions(mainActivity, Arrays.asList(arr));
	}

	public static void appInvite(String applinkUrl, String previewImageUrl) {
		if (AppInviteDialog.canShow()) {
			AppInviteContent content = new AppInviteContent.Builder()
					.setApplinkUrl(applinkUrl)
					.setPreviewImageUrl(previewImageUrl)
					.build();
			AppInviteDialog appInviteDialog = new AppInviteDialog(mainActivity);
			appInviteDialog.registerCallback(callbackManager, new FacebookCallback<AppInviteDialog.Result>() {
				@Override
				public void onSuccess(AppInviteDialog.Result result) {
					if (callbacks!=null) {
						Bundle bundle = result.getData();
						JSONObject json = new JSONObject();
						Set<String> keys = bundle.keySet();
						for (String key : keys) {
							try {
								json.put(key, wrap(bundle.get(key)));
							} catch (JSONException e) {
								Log.d(TAG, "JSONException: " + e.toString());
							}
						}
						callbacks.call1("_onAppInviteComplete", json.toString());
					}
				}

				@Override
				public void onCancel() {
					if (callbacks!=null) {
						callbacks.call1("_onAppInviteFail", "User canceled");
					}
				}

				@Override
				public void onError(FacebookException e) {
					if (callbacks!=null) {
						callbacks.call1("_onAppInviteFail", e.toString());
					}
				}
			});
			appInviteDialog.show(content);
		}
	}

	public static void shareLink(String contentURL, String contentTitle, String imageURL, String contentDescription) {
		ShareLinkContent.Builder builder = new ShareLinkContent.Builder();
		builder.setContentUrl(Uri.parse(contentURL));
		if (contentTitle!="") {
			builder.setContentTitle(contentTitle);
		}
		if (imageURL!="") {
			builder.setImageUrl(Uri.parse(imageURL));
		}
		if (contentDescription!="") {
			builder.setContentDescription(contentDescription);
		}
		ShareLinkContent content = builder.build();
		if (shareDialog!=null) {
			shareDialog.show(content);
		}
	}

	public static void appRequest(
		String message,
		String title,
		String recipients,
		String objectID,
		int actionType,
		String data
	) {
		GameRequestContent.Builder builder = new GameRequestContent.Builder();
		builder.setMessage(message);
		builder.setTitle(title);
		if (recipients!=null && recipients!="") {
			String[] arr = recipients.split(";");
			if (arr.length>0) {
				builder.setTo(arr[0]);
			}
		}
		if (objectID!=null & objectID!="") {
			builder.setObjectId(objectID);
		}
		switch (actionType) {
			case 1:
				builder.setActionType(ActionType.SEND);
				break;
			case 2:
				builder.setActionType(ActionType.ASKFOR);
				break;
			case 3:
				builder.setActionType(ActionType.TURN);
				break;
			default:
				builder.setActionType(ActionType.SEND);
		}
		if (data!=null && data!="") {
			builder.setData(data);
		}
		GameRequestContent content = builder.build();
		if (requestDialog!=null) {
			requestDialog.show(content);
		}
	}

	public static void graphRequest(
		String graphPath,
		String parametersJson,
		String methodStr,
		final int id
	) {

		Bundle bundle = new Bundle();
		try {
			JSONObject jsonObject = new JSONObject(parametersJson);
			bundle = BundleJSONConverter.convertToBundle(jsonObject);
		} catch (JSONException e) {
			Log.d(TAG, "JSONException: " + e.toString());
		}

		HttpMethod method;
		switch (methodStr.toUpperCase()) {
		case "DELETE":
			method = HttpMethod.DELETE;
			break;
		case "POST":
			method = HttpMethod.POST;
			break;
		default:
			method = HttpMethod.GET;
			break;
		}

		final GraphRequest req = new GraphRequest(
			AccessToken.getCurrentAccessToken(),
			graphPath,
			bundle,
			method,
			new GraphRequest.Callback() {
				@Override
				public void onCompleted(GraphResponse response) {
					if (callbacks!=null) {
						FacebookRequestError error = response.getError();
						GraphRequest req = response.getRequest();
						if (error==null) {
							callbacks.call3("_onGraphCallback", "ok", response.getRawResponse(), id);
						} else {
							String errorMessage;

							if (error.getRequestResult() == null) {
								errorMessage = "{}";
							} else {
								errorMessage = error.getRequestResult().toString();	
							}
							callbacks.call3("_onGraphCallback", "error", errorMessage, id);
						}
					}
				}

			}
		);
		mainActivity.runOnUiThread(new Runnable() {
			@Override	
			public void run() {
				req.executeAsync();
			}
		});

	}

	private static Map<String, String> getPayloadFromJson(String jsonString) {
        Type type = new TypeToken<Map<String, String>>(){}.getType();
        Map<String, String> payload = new Gson().fromJson(jsonString, type);
        return payload;
    }
	
	private static Bundle getAnalyticsBundleFromJson(String jsonString) {
        Map<String, String> payloadMap = getPayloadFromJson(jsonString);
        Bundle payloadBundle = new Bundle();
        for (Map.Entry<String, String> entry : payloadMap.entrySet()) {
            payloadBundle.putString(entry.getKey(), entry.getValue());
        }

        return payloadBundle;
    }

    public static void logEvent(String eventName, String jsonPayload)
    {
        Log.d(TAG, "log event " + eventName + " with payload: " + jsonPayload);

        Bundle payloadBundle = getAnalyticsBundleFromJson(jsonPayload);
        logger.logEvent(eventName, payloadBundle);
    }

    public static void setDebug()
    {
        Log.d(TAG, "DEBUG mode used");

        FacebookSdk.setIsDebugEnabled(true);
        FacebookSdk.addLoggingBehavior(LoggingBehavior.APP_EVENTS);
    }

    public static void setUserID(String userID) {
        Log.d(TAG, "setUserID to: " + userID);

        logger.setUserID(userID);
        logger.updateUserProperties(
                new Bundle(),
                new GraphRequest.Callback() {
                    @Override
                    public void onCompleted(GraphResponse response) {
                        if (callbacks!=null) {
                            FacebookRequestError error = response.getError();
                            GraphRequest req = response.getRequest();
                            if (error==null) {
                                Log.d(TAG, "on setUserID success");
                            } else {
                                String errorMessage;

                                if (error.getRequestResult() == null) {
                                    errorMessage = "{}";
                                } else {
                                    errorMessage = error.getRequestResult().toString();
                                }
                                Log.d(TAG, "on setUserID error: " + errorMessage);
                            }
                        }
                    }
                }
        );
    }

	public static void trackPurchase(float purchaseAmount, String currency, String parameters)
	{
		// Bundle parameters
		Bundle bundle = getAnalyticsBundleFromJson(parameters);
		logger.logPurchase(BigDecimal.valueOf(purchaseAmount), Currency.getInstance(currency), bundle);
	}

	// !Static methods interface
	
	@Override public void onCreate (Bundle savedInstanceState) {

		try {
			PackageInfo info = mainContext.getPackageManager().getPackageInfo(
				mainContext.getPackageName(),
				PackageManager.GET_SIGNATURES
			);
			for (Signature signature : info.signatures) {
				MessageDigest md = MessageDigest.getInstance("SHA");
				md.update(signature.toByteArray());
				Log.d(TAG, "KeyHash: " + Base64.encodeToString(md.digest(), Base64.DEFAULT));
			}
		} catch (NameNotFoundException e) {
			Log.d(TAG, "KeyHash: " + e.toString());
		} catch (NoSuchAlgorithmException e) {
			Log.d(TAG, "KeyHash: " + e.toString());
		}

	}

	@Override public boolean onActivityResult (int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if(callbackManager != null && !callbackManager.onActivityResult(requestCode, resultCode, data))
			Log.d(TAG, "callbackManager.onActivityResult cannot be handled requestCode: " + requestCode);
		return true;
	}

	@Override public void onDestroy() {
		if (accessTokenTracker != null) {
			accessTokenTracker.stopTracking();
		}
	}

}