# My Payment Speaker — Android Studio Source

## ଆବଶ୍ୟକତା
- Android Studio (ସାମ୍ପ୍ରତିକ stable version)
- JDK 17
- Android SDK Platform 36
- Internet (ପ୍ରଥମ Gradle sync ପାଇଁ)

## Build କରିବା
1. ZIP extract କରନ୍ତୁ।
2. Android Studio ରେ `MyPaymentSpeaker` folder Open କରନ୍ତୁ।
3. Gradle Sync ସମ୍ପୂର୍ଣ୍ଣ ହେବାକୁ ଦିଅନ୍ତୁ।
4. Build > Build Bundle(s) / APK(s) > Build APK(s) ବାଛନ୍ତୁ।
5. APK ସାଧାରଣତଃ `app/build/outputs/apk/debug/app-debug.apk` ରେ ମିଳିବ।

## ପ୍ରଥମ ସେଟଅପ୍
1. App ଖୋଲନ୍ତୁ।
2. Enable Notification Access ଦବାଇ My Payment Speaker କୁ ON କରନ୍ତୁ।
3. Android 13+ ରେ notification permission ଦିଅନ୍ତୁ।
4. Voice language ବାଛନ୍ତୁ ଏବଂ Test Voice କରନ୍ତୁ।
5. Battery settings ରେ ଆବଶ୍ୟକ ହେଲେ unrestricted/background permission ଦିଅନ୍ତୁ।

## ଗୁରୁତ୍ୱପୂର୍ଣ୍ଣ ସୀମା
- ଏହା notification-based prototype; UPI/bank API ସହିତ ସଂଯୁକ୍ତ ନୁହେଁ।
- ନିର୍ଦ୍ଦିଷ୍ଟ package IDs ର notification ମାତ୍ର ଧରୁଛି। ଅନ୍ୟ UPI apps ପାଇଁ package ID ଯୋଡ଼ିବାକୁ ପଡ଼ିବ।
- App notification ରେ payment text/amount ଥିବା ଦରକାର। Notification format ବଦଳିଲେ parser update ଦରକାର ହୋଇପାରେ।
- Notification ଆଧାରରେ payment ସତ୍ୟାପିତ ହୁଏ ନାହିଁ। ଟଙ୍କା ଆସିଛି କି ନାହିଁ bank statement/balance ରେ ଯାଞ୍ଚ କରନ୍ତୁ।
- Android ର TTS API ସମସ୍ତ ଫୋନ୍‌ରେ female voice ନିଶ୍ଚିତ କରେ ନାହିଁ। ଏହା ଫୋନ୍‌ରେ install ଥିବା TTS engine ଓ language voice ଉପରେ ନିର୍ଭର କରେ।
- Android 8 ରୁ 16+ ପର୍ଯ୍ୟନ୍ତ source compatibility ଲକ୍ଷ୍ୟ; ସମସ୍ତ ଡିଭାଇସ୍‌ରେ ପରୀକ୍ଷା ହୋଇନାହିଁ।
- ଏହି source ଏକ starter prototype; public release ପୂର୍ବରୁ device testing, notification parsing hardening, privacy policy ଓ Play policy review ଆବଶ୍ୟକ।

## Privacy
Notification content ଡିଭାଇସ୍‌ରେ ହିଁ ପଢ଼ାଯାଏ; ଏହି prototype ରେ server କୁ ପଠାଯାଏ ନାହିଁ। Notification access ଦେବା ପୂର୍ବରୁ ଏହା ବୁଝି ଅନୁମତି ଦିଅନ୍ତୁ।
