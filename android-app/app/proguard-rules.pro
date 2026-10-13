# WorkManager instancie sa base Room par réflexion (constructeur sans argument).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.** { <init>(...); }
