# Proguard rules for Android Background Switcher

# Room
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public void clearAllTables();
}

# Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }

# WorkManager
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
