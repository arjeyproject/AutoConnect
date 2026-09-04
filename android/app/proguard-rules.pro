# The HEV Socks5 Tunnel JNI contract is resolved by name from native code.
-keep class hev.htproxy.TProxyService { *; }
-keepclasseswithmembernames class * { native <methods>; }
# View binding + custom views inflated from XML.
-keep class io.github.arjeyproject.autoconnect.ConnectionOrbView { *; }
-keepclassmembers class * extends android.view.View {
    public <init>(android.content.Context, android.util.AttributeSet);
}
