# R8 rules for release builds. The app itself uses no reflection; Glance and WorkManager make
# a few classes by name, and their own rules cover them. These are a belt-and-braces copy for the
# app's classes they make: the widget tick action and the background refresh.
-keep class app.slate.notes.widgets.TickItem { <init>(); }
-keep class app.slate.notes.widgets.WidgetRefreshWorker { <init>(...); }

# WorkManager keeps its queue in a Room database, which Room makes by name through the generated
# class's empty constructor. R8 removed that constructor and the release crashed on launch, so
# every Room database keeps it.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
