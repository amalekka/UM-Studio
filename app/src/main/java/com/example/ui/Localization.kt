package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

object Localization {
    enum class Lang { EN, AR }

    private val translations = mapOf(
        "READY" to mapOf(Lang.EN to "READY", Lang.AR to "جاهز للبث"),
        "LIVE" to mapOf(Lang.EN to "LIVE", Lang.AR to "مباشر"),
        "PAUSED" to mapOf(Lang.EN to "PAUSED", Lang.AR to "متوقف مؤقتاً"),
        "CONNECTING" to mapOf(Lang.EN to "CONNECTING", Lang.AR to "جاري الاتصال..."),
        "DROP" to mapOf(Lang.EN to "DROP", Lang.AR to "فقد"),
        "FPS" to mapOf(Lang.EN to "FPS", Lang.AR to "إطار/ث"),
        "REC" to mapOf(Lang.EN to "REC", Lang.AR to "تسجيل"),
        
        // Scenes
        "MAIN_LIVE" to mapOf(Lang.EN to "Main Live", Lang.AR to "البث الرئيسي"),
        "STARTING_SOON" to mapOf(Lang.EN to "Starting Soon", Lang.AR to "يبدأ قريباً"),
        "BE_RIGHT_BACK" to mapOf(Lang.EN to "Be Right Back", Lang.AR to "فاصل ونعود"),
        "JUST_CHATTING" to mapOf(Lang.EN to "Just Chatting", Lang.AR to "دردشة حية"),
        
        // Sources
        "CAM" to mapOf(Lang.EN to "CAM", Lang.AR to "كاميرا"),
        "M3U8" to mapOf(Lang.EN to "M3U8 URL", Lang.AR to "رابط بث"),
        "SCREEN" to mapOf(Lang.EN to "SCREEN", Lang.AR to "الشاشة"),
        "AUDIO" to mapOf(Lang.EN to "AUDIO", Lang.AR to "صوت فقط"),
        
        // Aspect/Scale
        "FIT" to mapOf(Lang.EN to "FIT", Lang.AR to "احتواء"),
        "FILL" to mapOf(Lang.EN to "FILL", Lang.AR to "ملء الشاشة"),
        
        // Bottom Dock
        "GO_LIVE" to mapOf(Lang.EN to "GO LIVE", Lang.AR to "بدء البث"),
        "STOP" to mapOf(Lang.EN to "STOP", Lang.AR to "إيقاف"),
        "PAUSE" to mapOf(Lang.EN to "PAUSE", Lang.AR to "إيقاف مؤقت"),
        "MIC_ON" to mapOf(Lang.EN to "MIC ON", Lang.AR to "المايك"),
        "MIC_OFF" to mapOf(Lang.EN to "MUTED", Lang.AR to "صامت"),
        "MIC" to mapOf(Lang.EN to "MIC", Lang.AR to "المايك"),
        "MIXER" to mapOf(Lang.EN to "MIXER", Lang.AR to "الصوت"),
        "LAYERS" to mapOf(Lang.EN to "LAYERS", Lang.AR to "الطبقات"),
        "DESTS" to mapOf(Lang.EN to "DESTS", Lang.AR to "المنصات"),
        
        // Side FABs
        "CHAT" to mapOf(Lang.EN to "CHAT", Lang.AR to "الشات"),
        "FLIP" to mapOf(Lang.EN to "FLIP", Lang.AR to "قلب"),
        "PIP" to mapOf(Lang.EN to "PIP", Lang.AR to "نافذة"),
        "RESET" to mapOf(Lang.EN to "RESET", Lang.AR to "ضبط"),
        
        // Modal Titles
        "STREAM_LINKS" to mapOf(Lang.EN to "Stream Links", Lang.AR to "روابط البث"),
        "STREAM_LINKS_SUB" to mapOf(Lang.EN to "Add or select a live M3U8 or video URL", Lang.AR to "أضف رابط M3U8 أو فيديو مباشر"),
        "AUDIO_MIXER" to mapOf(Lang.EN to "Audio Mixer", Lang.AR to "مازج الصوت"),
        "AUDIO_MIXER_SUB" to mapOf(Lang.EN to "Adjust mic, stream volume & sound effects", Lang.AR to "التحكم في المايك، صوت البث والمؤثرات"),
        "STUDIO_LAYERS" to mapOf(Lang.EN to "Studio Layers", Lang.AR to "طبقات البث"),
        "STUDIO_LAYERS_SUB" to mapOf(Lang.EN to "Backgrounds, text banners & emojis", Lang.AR to "الخلفيات، النصوص والملصقات"),
        "PLATFORMS" to mapOf(Lang.EN to "Platforms", Lang.AR to "منصات البث"),
        "PLATFORMS_SUB" to mapOf(Lang.EN to "Select destinations to go live", Lang.AR to "اختر المنصات لتشغيل البث المباشر عليها"),
        "STREAM_SETTINGS" to mapOf(Lang.EN to "Stream Settings", Lang.AR to "إعدادات البث"),
        "STREAM_SETTINGS_SUB" to mapOf(Lang.EN to "Video quality, bitrate & encoder", Lang.AR to "جودة الفيديو، الأبعاد والمشفر"),
        "SCREEN_BROADCAST" to mapOf(Lang.EN to "Screen Broadcast", Lang.AR to "بث شاشة الهاتف"),
        "SCREEN_BROADCAST_SUB" to mapOf(Lang.EN to "Share your phone screen live", Lang.AR to "مشاركة شاشة الهاتف والألعاب مباشرة"),
        
        // Components/Buttons
        "SAVE" to mapOf(Lang.EN to "SAVE", Lang.AR to "حفظ"),
        "TEST_ACTIVE" to mapOf(Lang.EN to "TEST & ACTIVE", Lang.AR to "تشغيل وتفعيل"),
        "BACK_TO_STUDIO" to mapOf(Lang.EN to "Back to Studio", Lang.AR to "العودة للاستوديو"),
        "START_SCREEN_SHARE" to mapOf(Lang.EN to "Start Screen Share", Lang.AR to "بدء مشاركة الشاشة"),
        "DONE" to mapOf(Lang.EN to "DONE", Lang.AR to "تم"),
        
        // Mixer Channels
        "CH_MIC" to mapOf(Lang.EN to "Mic", Lang.AR to "صوت المايك"),
        "CH_STREAM" to mapOf(Lang.EN to "Stream Audio", Lang.AR to "صوت البث"),
        "CH_BGM" to mapOf(Lang.EN to "Background Music", Lang.AR to "موسيقى خلفية"),
        "MUTE" to mapOf(Lang.EN to "MUTE", Lang.AR to "كتم"),
        "UNMUTE" to mapOf(Lang.EN to "UNMUTE", Lang.AR to "تفعيل"),
        "PICK_AUDIO" to mapOf(Lang.EN to "Pick Audio File", Lang.AR to "اختيار ملف صوتي"),
        "NOISE_GATE" to mapOf(Lang.EN to "Noise Gate", Lang.AR to "عزل الضوضاء"),
        "ECHO_CANCEL" to mapOf(Lang.EN to "Echo Cancel", Lang.AR to "منع الصدى"),
        
        // Layers Tabs
        "TAB_BG" to mapOf(Lang.EN to "Background", Lang.AR to "الخلفية"),
        "TAB_OVERLAYS" to mapOf(Lang.EN to "Overlays", Lang.AR to "الإضافات"),
        "TAB_TEXT" to mapOf(Lang.EN to "Add Text", Lang.AR to "إضافة نص"),
        "TAB_EMOJI" to mapOf(Lang.EN to "Add Emoji", Lang.AR to "إضافة إيموجي"),
        "CHOOSE_GALLERY" to mapOf(Lang.EN to "Choose Gallery Image", Lang.AR to "اختيار صورة من المعرض"),
        
        // Platforms Status
        "ACTIVE" to mapOf(Lang.EN to "Active", Lang.AR to "مفعل"),
        "DISABLED" to mapOf(Lang.EN to "Disabled", Lang.AR to "متوقف"),
        "STREAM_KEY" to mapOf(Lang.EN to "Stream Key", Lang.AR to "مفتاح البث"),
        "SERVER_URL" to mapOf(Lang.EN to "Server URL", Lang.AR to "رابط السيرفر"),
        
        // Settings Labels
        "RESOLUTION" to mapOf(Lang.EN to "Resolution", Lang.AR to "دقة العرض"),
        "FRAMERATE" to mapOf(Lang.EN to "Framerate", Lang.AR to "معدل الإطارات"),
        "BITRATE" to mapOf(Lang.EN to "Video Bitrate", Lang.AR to "سرعة البث"),
        "HW_ACCEL" to mapOf(Lang.EN to "Hardware Accel", Lang.AR to "تسريع المعالج"),
        "WATERMARK" to mapOf(Lang.EN to "Watermark", Lang.AR to "شعار مائي"),
        "TICKER" to mapOf(Lang.EN to "Scrolling Ticker", Lang.AR to "شريط إخباري"),
        "APP_LANGUAGE" to mapOf(Lang.EN to "App Language / لغة التطبيق", Lang.AR to "لغة التطبيق / App Language"),
        
        // Settings Sub-labels
        "BROADCAST_RES" to mapOf(Lang.EN to "BROADCAST RESOLUTION", Lang.AR to "دقة البث"),
        "STAGE_ORIENT" to mapOf(Lang.EN to "STAGE ORIENTATION", Lang.AR to "توجيه الشاشة"),
        "LANDSCAPE" to mapOf(Lang.EN to "Landscape (16:9)", Lang.AR to "عرضي (16:9)"),
        "PORTRAIT" to mapOf(Lang.EN to "Portrait (9:16)", Lang.AR to "طولي (9:16)"),
        "OUTPUT_FPS" to mapOf(Lang.EN to "OUTPUT FRAMERATE", Lang.AR to "معدل الإطارات"),
        "FPS_30" to mapOf(Lang.EN to "30 FPS (Standard)", Lang.AR to "30 إطار (قياسي)"),
        "FPS_60" to mapOf(Lang.EN to "60 FPS (Smooth Pro)", Lang.AR to "60 إطار (سلس)"),
        "KEYFRAME_INT" to mapOf(Lang.EN to "KEYFRAME INTERVAL: GOP = 2.0s (LOCKED)", Lang.AR to "فاصل الإطارات الرئيسية: 2.0 ثانية"),
        "KEYFRAME_DESC" to mapOf(Lang.EN to "Mandatory 2-second keyframe cadence strictly enforced for Twitch, YouTube & Telegram live delivery.", Lang.AR to "إلزامي لمنصات يوتيوب وتويتش وتيليجرام لضمان استقرار البث."),
        "TARGET_BITRATE" to mapOf(Lang.EN to "TARGET VIDEO BITRATE", Lang.AR to "سرعة البث المستهدفة"),
        "ABR" to mapOf(Lang.EN to "ADAPTIVE BITRATE (ABR)", Lang.AR to "سرعة بث متغيرة (ABR)"),
        "ABR_DESC" to mapOf(Lang.EN to "Automatically lowers bitrate if network bandwidth drops, preventing stream buffer stalls or disconnects.", Lang.AR to "يقلل الجودة تلقائياً عند ضعف الإنترنت لمنع التقطيع."),
        "ENCODER_BACKEND" to mapOf(Lang.EN to "ENCODER BACKEND", Lang.AR to "محرك التشفير"),
        "ENC_HW" to mapOf(Lang.EN to "Hardware MediaCodec H.264", Lang.AR to "Hardware MediaCodec H.264"),
        "ENC_SW" to mapOf(Lang.EN to "Software x264 (Ultrafast)", Lang.AR to "Software x264 (Ultrafast)"),
        "KEEP_AWAKE" to mapOf(Lang.EN to "KEEP SCREEN AWAKE", Lang.AR to "إبقاء الشاشة مفعلة"),
        "BG_SERVICE" to mapOf(Lang.EN to "BACKGROUND STREAMING SERVICE", Lang.AR to "البث في الخلفية"),
        "AUDIO_CONFIG" to mapOf(Lang.EN to "AAC-LC 128 kbps 44.1 kHz STEREO AUDIO", Lang.AR to "AAC-LC 128 kbps 44.1 kHz STEREO AUDIO"),
        "SCOREBOARD" to mapOf(Lang.EN to "LIVE MATCH SCOREBOARD", Lang.AR to "لوحة نتائج المباراة"),
        "SCREEN_SHARE_DESC" to mapOf(Lang.EN to "Games, tutorials, and applications will broadcast directly to active destinations.", Lang.AR to "سيتم بث الألعاب والبرامج والشروحات مباشرة إلى منصات البث المختارة."),
        "EXIT_APP" to mapOf(Lang.EN to "Exit Application", Lang.AR to "خروج من التطبيق"),
        "EXIT_DESC" to mapOf(Lang.EN to "Do you want to stop streaming, stop background services, and exit the app completely?", Lang.AR to "هل تريد إيقاف البث والخدمات والخروج من التطبيق نهائياً؟"),
        
        // Destinations Screen
        "READINESS" to mapOf(Lang.EN to "READINESS", Lang.AR to "حالة الجاهزية"),
        "MAJOR_PLATFORMS" to mapOf(Lang.EN to "Major Platforms", Lang.AR to "المنصات الرئيسية"),
        "CUSTOM_SERVERS" to mapOf(Lang.EN to "Custom RTMP Servers", Lang.AR to "سيرفرات RTMP مخصصة"),
        "ADD_SERVER" to mapOf(Lang.EN to "ADD SERVER", Lang.AR to "إضافة سيرفر"),
        "NO_CUSTOM_SERVERS" to mapOf(Lang.EN to "No Custom Servers Added", Lang.AR to "لا توجد سيرفرات مخصصة"),
        "CUSTOM_SERVERS_DESC" to mapOf(Lang.EN to "Add custom RTMP, RTMPS, or SRT relays for Telegram, private servers, or CDN ingests.", Lang.AR to "أضف روابط RTMP أو RTMPS مخصصة للبث على تيليجرام أو سيرفرات خاصة."),
        "CUSTOM_SERVER_LABEL" to mapOf(Lang.EN to "CUSTOM SERVER LABEL", Lang.AR to "اسم السيرفر المخصص"),
        "INGEST_URL" to mapOf(Lang.EN to "RTMP / RTMPS / SRT INGEST URL", Lang.AR to "رابط البث (URL)"),
        "RESTORE_DEFAULT" to mapOf(Lang.EN to "RESTORE DEFAULT", Lang.AR to "استعادة الافتراضي"),
        "TEST_PING" to mapOf(Lang.EN to "TEST PING", Lang.AR to "اختبار البث"),
        "TESTING_ELLIPSIS" to mapOf(Lang.EN to "TESTING...", Lang.AR to "جاري الاختبار..."),
        "STREAM_KEY_PASTE_HINT" to mapOf(Lang.EN to "Paste your Stream Key", Lang.AR to "الصق مفتاح البث هنا"),
        "PASTE" to mapOf(Lang.EN to "PASTE", Lang.AR to "لصق"),
        "DELETE" to mapOf(Lang.EN to "DELETE", Lang.AR to "حذف"),
        "ADD_CUSTOM_DESTINATION" to mapOf(Lang.EN to "ADD CUSTOM RTMP DESTINATION", Lang.AR to "إضافة وجهة بث مخصصة"),
        "NAME_LABEL" to mapOf(Lang.EN to "NAME / LABEL", Lang.AR to "الاسم / التسمية"),
        "SERVER_URL_HINT" to mapOf(Lang.EN to "SERVER URL (rtmp://, rtmps://, or srt://)", Lang.AR to "رابط السيرفر (rtmp, rtmps, srt)"),
        "SAVE_SERVER" to mapOf(Lang.EN to "SAVE SERVER", Lang.AR to "حفظ السيرفر"),
        "CUSTOM_SERVER_PLACEHOLDER" to mapOf(Lang.EN to "e.g. My Telegram Stream, Nginx Relay", Lang.AR to "مثال: بث تيليجرام، سيرفر خاص"),
        "INGEST_URL_PLACEHOLDER" to mapOf(Lang.EN to "rtmp://my-server.com/live", Lang.AR to "rtmp://server.com/live"),
        "STREAM_KEY_PLACEHOLDER" to mapOf(Lang.EN to "Stream Key (optional for open relays)", Lang.AR to "مفتاح البث (اختياري للسيرفرات المفتوحة)"),
        "TESTING_CONNECTION" to mapOf(Lang.EN to "Testing connection...", Lang.AR to "جاري اختبار الاتصال..."),
        
        // M3U8 Screen
        "ONE_TAP_RELAY" to mapOf(Lang.EN to "ONE-TAP RELAY", Lang.AR to "بث بضغطة واحدة"),
        "NO_M3U8_SERVERS" to mapOf(Lang.EN to "No M3U8 Servers Saved", Lang.AR to "لا توجد روابط بث محفوظة"),
        "M3U8_SERVERS_DESC" to mapOf(Lang.EN to "Add your favorite live HLS streams above for quick playback.", Lang.AR to "أضف روابط HLS المفضلة لديك هنا لتشغيلها بسرعة."),
        "ADD_NEW_M3U8" to mapOf(Lang.EN to "ADD NEW M3U8 SERVER", Lang.AR to "إضافة رابط بث M3U8 جديد"),
        "QUICK_TEST_PRESETS" to mapOf(Lang.EN to "QUICK TEST PRESETS (TAP TO LOAD)", Lang.AR to "روابط تجريبية جاهزة (اضغط للتحميل)"),
        "SERVER_STREAM_NAME" to mapOf(Lang.EN to "SERVER / STREAM NAME (e.g., Live Sport Feed)", Lang.AR to "اسم البث (مثال: قناة رياضية)"),
        "M3U8_URL_LABEL" to mapOf(Lang.EN to "M3U8 / HLS URL (.m3u8)", Lang.AR to "رابط البث (M3U8 / HLS)"),
        "HTTP_HEADERS_OPTIONAL" to mapOf(Lang.EN to "OPTIONAL HTTP HEADERS (USER-AGENT / REFERER)", Lang.AR to "رؤوس HTTP اختيارية (User-Agent / Referer)"),
        "CURRENTLY_PLAYING" to mapOf(Lang.EN to "CURRENTLY PLAYING", Lang.AR to "قيد التشغيل الآن"),
        "CURRENTLY_LOADED" to mapOf(Lang.EN to "CURRENTLY LOADED", Lang.AR to "تم التحميل"),
        "LOAD_PLAY_STUDIO" to mapOf(Lang.EN to "LOAD & PLAY ON STUDIO", Lang.AR to "تشغيل في الاستوديو"),
        "EDIT_M3U8_SERVER" to mapOf(Lang.EN to "EDIT M3U8 SERVER", Lang.AR to "تعديل رابط البث"),
        
        // Defaults & Status
        "LIVE_STREAM" to mapOf(Lang.EN to "LIVE STREAM", Lang.AR to "بث مباشر"),
        "TEAM_A" to mapOf(Lang.EN to "TEAM A", Lang.AR to "الفريق أ"),
        "TEAM_B" to mapOf(Lang.EN to "TEAM B", Lang.AR to "الفريق ب"),
        "STREAM_STARTING_SOON" to mapOf(Lang.EN to "STREAM STARTING SOON", Lang.AR to "البث يبدأ قريباً"),
        "NO_STREAM_LOADED" to mapOf(Lang.EN to "No Stream Loaded", Lang.AR to "لا يوجد بث محمل"),
        "VOLUME" to mapOf(Lang.EN to "VOLUME", Lang.AR to "مستوى الصوت"),
        "ULTRA_FAST_COPY" to mapOf(Lang.EN to "ULTRA-FAST STREAM COPY", Lang.AR to "نسخ البث فائق السرعة"),
        "STREAM_COPY_DESC" to mapOf(Lang.EN to "Direct passthrough without re-encoding (0% CPU, 60 FPS)", Lang.AR to "بث مباشر بدون إعادة تشفير (استهلاك 0% من المعالج)"),
        "STATUS" to mapOf(Lang.EN to "STATUS", Lang.AR to "الحالة"),
        "NOISE_GATE_LABEL" to mapOf(Lang.EN to "NOISE GATE", Lang.AR to "بوابة الضوضاء"),
        "ECHO_CANCEL_LABEL" to mapOf(Lang.EN to "ECHO CANCEL", Lang.AR to "إلغاء الصدى"),
        "SYNC_DELAY" to mapOf(Lang.EN to "AUDIO SYNC DELAY", Lang.AR to "تأخير مزامنة الصوت"),
        "AUDIO_DUCKING" to mapOf(Lang.EN to "AUDIO DUCKING", Lang.AR to "خفض الصوت التلقائي"),
        "LIVE_CHAT" to mapOf(Lang.EN to "LIVE CHAT", Lang.AR to "الدردشة المباشرة"),
        "PINNED_COMMENT" to mapOf(Lang.EN to "PINNED COMMENT ON STREAM", Lang.AR to "تعليق مثبت على البث"),
        "UNPIN" to mapOf(Lang.EN to "UNPIN", Lang.AR to "إلغاء التثبيت"),
        "CONNECT_TWITCH" to mapOf(Lang.EN to "CONNECT TWITCH CHAT", Lang.AR to "ربط شات تويتش"),
        "CONNECT_YOUTUBE" to mapOf(Lang.EN to "CONNECT YOUTUBE CHAT", Lang.AR to "ربط شات يوتيوب"),
        "REMOTE_MANAGER" to mapOf(Lang.EN to "PRO REMOTE STREAM MANAGER", Lang.AR to "مدير البث عن بعد"),
        "RTMP_INGEST_URL" to mapOf(Lang.EN to "RTMP SERVER INGEST URL", Lang.AR to "رابط سيرفر RTMP"),
        "RESET_DEFAULT" to mapOf(Lang.EN to "RESET DEFAULT", Lang.AR to "إعادة للافتراضي"),

        // New Dialogs & Stage Translations
        "CREATE_NEW_LIVE_SCENE" to mapOf(Lang.EN to "CREATE NEW LIVE SCENE", Lang.AR to "إنشاء مشهد بث جديد"),
        "SCENE_NAME_LABEL" to mapOf(Lang.EN to "SCENE NAME (e.g. Gaming / Highlights)", Lang.AR to "اسم المشهد (مثال: ألعاب / ملخصات)"),
        "SCENE_NAME_PLACEHOLDER" to mapOf(Lang.EN to "Enter scene name...", Lang.AR to "أدخل اسم المشهد..."),
        "CHOOSE_ICON" to mapOf(Lang.EN to "CHOOSE ICON", Lang.AR to "اختر أيقونة"),
        "ADD_SCENE" to mapOf(Lang.EN to "ADD SCENE", Lang.AR to "إضافة المشهد"),
        "CANCEL" to mapOf(Lang.EN to "CANCEL", Lang.AR to "إلغاء"),
        "ADD_SCENE_BTN" to mapOf(Lang.EN to "+ Scene", Lang.AR to "+ مشهد جديد"),
        "CHAT_WAITING" to mapOf(Lang.EN to "💬 Chat Connected • Waiting for messages...", Lang.AR to "💬 الشات متصل • بانتظار الرسائل..."),
        "STAGE_CINEMA" to mapOf(Lang.EN to "16:9 CINEMA STAGE", Lang.AR to "عرض سينمائي 16:9"),
        "STAGE_VERTICAL" to mapOf(Lang.EN to "9:16 VERTICAL STAGE", Lang.AR to "عرض عمودي 9:16"),
        "STAGE_SQUARE" to mapOf(Lang.EN to "1:1 SQUARE STAGE", Lang.AR to "عرض مربع 1:1"),
        "STAGE_CUSTOM" to mapOf(Lang.EN to "CUSTOM BROADCAST STAGE", Lang.AR to "عرض مخصص"),
        "CUSTOM_TEXT_OVERLAYS" to mapOf(Lang.EN to "CUSTOM TEXT OVERLAYS", Lang.AR to "نصوص مخصصة للبث"),
        "ADD_TEXT_LAYER" to mapOf(Lang.EN to "ADD TEXT LAYER (ARABIC / ENGLISH)", Lang.AR to "إضافة طبقة نصية (عربي / إنجليزي)"),
        "NO_ACTIVE_TEXT" to mapOf(Lang.EN to "No active text layers. Tap above to add one.", Lang.AR to "لا توجد طبقات نصية. اضغط أعلاه لإضافة نص."),
        "CONTENT_LABEL" to mapOf(Lang.EN to "CONTENT (ARABIC / ENGLISH)", Lang.AR to "المحتوى النصي"),
        "TEXT_COLOR" to mapOf(Lang.EN to "TEXT COLOR", Lang.AR to "لون النص"),
        "NEON_GLOW_COLOR" to mapOf(Lang.EN to "NEON GLOW COLOR", Lang.AR to "لون التوهج"),
        "PILL_BG_COLOR" to mapOf(Lang.EN to "PILL BACKGROUND COLOR", Lang.AR to "لون خلفية النص"),
        "FONT_SIZE" to mapOf(Lang.EN to "FONT SIZE", Lang.AR to "حجم الخط"),
        "OPACITY" to mapOf(Lang.EN to "OPACITY", Lang.AR to "الشفافية"),
        "BOTTOM_NEWS_TICKER" to mapOf(Lang.EN to "BOTTOM BREAKING NEWS TICKER", Lang.AR to "شريط الأخبار العاجلة"),
        "ENABLE_NEWS_TICKER" to mapOf(Lang.EN to "ENABLE BOTTOM NEWS TICKER", Lang.AR to "تفعيل شريط الأخبار"),
        "TICKER_HEADLINE" to mapOf(Lang.EN to "TICKER HEADLINE BANNER", Lang.AR to "عنوان شريط الأخبار"),
        "SCROLL_SPEED" to mapOf(Lang.EN to "SCROLL SPEED", Lang.AR to "سرعة التمرير"),
        "ACCENT_GLOW_COLOR" to mapOf(Lang.EN to "ACCENT GLOW COLOR", Lang.AR to "لون التميز"),
        "LIVE_MATCH_SCOREBOARD" to mapOf(Lang.EN to "LIVE MATCH SCOREBOARD OVERLAY", Lang.AR to "لوحة نتائج المباراة الحية"),
        "ENABLE_SCOREBOARD" to mapOf(Lang.EN to "ENABLE LIVE MATCH SCOREBOARD", Lang.AR to "تفعيل لوحة النتائج"),
        "MATCH_PERIOD" to mapOf(Lang.EN to "MATCH PERIOD", Lang.AR to "شوط المباراة"),
        "TEAM_A_HOME" to mapOf(Lang.EN to "TEAM A (HOME)", Lang.AR to "الفريق الأول (صاحب الأرض)"),
        "TEAM_B_AWAY" to mapOf(Lang.EN to "TEAM B (AWAY)", Lang.AR to "الفريق الثاني (الضيف)"),
        "IMPORT_M3U_PLAYLIST" to mapOf(Lang.EN to "IMPORT M3U PLAYLIST", Lang.AR to "استيراد قائمة M3U"),
        "IMPORT_CHANNELS" to mapOf(Lang.EN to "IMPORT CHANNELS", Lang.AR to "استيراد القنوات"),
        "PASTE_FROM_CLIPBOARD" to mapOf(Lang.EN to "PASTE FROM CLIPBOARD", Lang.AR to "لصق من الحافظة"),
        "RESTORE_PROFILES" to mapOf(Lang.EN to "RESTORE STREAM PROFILES", Lang.AR to "استعادة ملفات البث"),
        "IMPORT" to mapOf(Lang.EN to "IMPORT", Lang.AR to "استيراد"),

        // Studio Layers / Compositor Translations
        "IMAGE_SOURCE" to mapOf(Lang.EN to "IMAGE SOURCE", Lang.AR to "مصدر الصورة"),
        "DIRECT_IMAGE_URL" to mapOf(Lang.EN to "DIRECT IMAGE URL", Lang.AR to "رابط صورة مباشر"),
        "CANVAS_CONTROLS" to mapOf(Lang.EN to "CANVAS CONTROLS", Lang.AR to "تحكم مساحة العرض"),
        "PRESET_LAYOUT_MODE" to mapOf(Lang.EN to "PRESET LAYOUT MODE", Lang.AR to "وضع العرض"),
        "MODE_COVER" to mapOf(Lang.EN to "Cover", Lang.AR to "تغطية"),
        "MODE_CONTAIN" to mapOf(Lang.EN to "Contain", Lang.AR to "احتواء"),
        "MODE_TOP" to mapOf(Lang.EN to "Top", Lang.AR to "أعلى"),
        "MODE_BOTTOM" to mapOf(Lang.EN to "Bottom", Lang.AR to "أسفل"),
        "MODE_STRETCH" to mapOf(Lang.EN to "Stretch", Lang.AR to "تمطيط"),
        "ZOOM_SCALE" to mapOf(Lang.EN to "ZOOM / SCALE", Lang.AR to "التكبير / الحجم"),
        "X_OFFSET" to mapOf(Lang.EN to "X OFFSET", Lang.AR to "إزاحة سينية (X)"),
        "Y_OFFSET" to mapOf(Lang.EN to "Y OFFSET", Lang.AR to "إزاحة صادية (Y)"),
        "GAUSSIAN_BLUR" to mapOf(Lang.EN to "GAUSSIAN BLUR", Lang.AR to "التمويه الضبابي"),
        "ADD_TEXT" to mapOf(Lang.EN to "ADD TEXT", Lang.AR to "إضافة نص"),
        "ADD_EMOJI" to mapOf(Lang.EN to "ADD EMOJI", Lang.AR to "إضافة إيموجي"),
        "ACTIVE_LAYERS" to mapOf(Lang.EN to "ACTIVE LAYERS", Lang.AR to "الطبقات النشطة"),
        "ANIMATED_EMOJI_BURST" to mapOf(Lang.EN to "ANIMATED EMOJI BURST", Lang.AR to "تفاعل الإيموجي المتحرك"),
        "EMOJI_BURST_DESC" to mapOf(Lang.EN to "Tap an emoji to trigger a 2-second burst on the live stream.", Lang.AR to "اضغط على أي إيموجي لإرساله على شاشة البث."),

        // Destinations Hub Translations
        "STREAM_DESTINATIONS" to mapOf(Lang.EN to "Stream Destinations", Lang.AR to "منصات البث"),
        "STREAM_DESTINATIONS_SUB" to mapOf(Lang.EN to "Enable and configure where you want to broadcast live.", Lang.AR to "إدارة وتفعيل جهات البث المباشر"),
        "EXPORT_BACKUP" to mapOf(Lang.EN to "EXPORT BACKUP", Lang.AR to "تصدير نسخة"),
        "RESTORE_BACKUP" to mapOf(Lang.EN to "RESTORE BACKUP", Lang.AR to "استعادة نسخة"),
        "ADD_CUSTOM_RTMP_BTN" to mapOf(Lang.EN to "+ CUSTOM RTMP", Lang.AR to "+ سيرفر مخصص"),
        "YOUTUBE_LIVE" to mapOf(Lang.EN to "YOUTUBE LIVE", Lang.AR to "يوتيوب مباشر"),
        "FACEBOOK_LIVE" to mapOf(Lang.EN to "FACEBOOK LIVE", Lang.AR to "فيسبوك مباشر"),
        "TIKTOK_LIVE" to mapOf(Lang.EN to "TIKTOK LIVE", Lang.AR to "تيك توك مباشر"),
        "TWITCH_LIVE" to mapOf(Lang.EN to "TWITCH", Lang.AR to "تويتش"),
        "TWITTER_LIVE" to mapOf(Lang.EN to "X (TWITTER) / PERISCOPE", Lang.AR to "اكس (تويتر) / بيريسكوب"),
        "INSTAGRAM_LIVE" to mapOf(Lang.EN to "INSTAGRAM LIVE", Lang.AR to "إنستغرام مباشر"),
        "TELEGRAM_LIVE" to mapOf(Lang.EN to "TELEGRAM LIVE", Lang.AR to "تليجرام مباشر"),
        "CUSTOM_RTMP_LIVE" to mapOf(Lang.EN to "CUSTOM RTMP", Lang.AR to "سيرفر RTMP مخصص"),
        "STATUS_ACTIVE" to mapOf(Lang.EN to "ACTIVE", Lang.AR to "نشط"),
        "STATUS_DISABLED" to mapOf(Lang.EN to "DISABLED", Lang.AR to "متوقف"),
        "KEY_REQUIRED" to mapOf(Lang.EN to "KEY REQUIRED", Lang.AR to "المفتاح مطلوب"),
        "AUTO_RECONNECTING" to mapOf(Lang.EN to "Auto-Reconnecting...", Lang.AR to "إعادة الاتصال تلقائياً..."),
        "CONNECTING" to mapOf(Lang.EN to "Connecting...", Lang.AR to "جاري الاتصال..."),
        "SERVER_URL_LABEL" to mapOf(Lang.EN to "SERVER URL", Lang.AR to "رابط السيرفر"),
        "STREAM_KEY_LABEL" to mapOf(Lang.EN to "STREAM KEY", Lang.AR to "مفتاح البث"),
        "SAVE_CREDENTIALS" to mapOf(Lang.EN to "SAVE CREDENTIALS", Lang.AR to "حفظ البيانات"),
        "CUSTOM_SERVER_LABEL" to mapOf(Lang.EN to "CUSTOM SERVER LABEL", Lang.AR to "تسمية السيرفر المخصص"),
        "RTMP_INGEST_URL" to mapOf(Lang.EN to "RTMP / RTMPS / SRT INGEST URL", Lang.AR to "رابط بث RTMP / RTMPS / SRT"),
        "SAVE_CHANGES" to mapOf(Lang.EN to "SAVE CHANGES", Lang.AR to "حفظ التغيرات"),
        "TEST_RTMP" to mapOf(Lang.EN to "TEST RTMP", Lang.AR to "اختبار RTMP"),
        "PROBING" to mapOf(Lang.EN to "PROBING...", Lang.AR to "جاري الفحص..."),
        "TEST" to mapOf(Lang.EN to "TEST", Lang.AR to "اختبار")
    )

    fun getString(key: String, lang: Lang): String {
        return translations[key]?.get(lang) ?: key
    }
}

@Composable
fun localizedString(key: String, viewModel: MainViewModel): String {
    val uiState by viewModel.uiState.collectAsState()
    val lang = if (uiState.appLang == "ar") Localization.Lang.AR else Localization.Lang.EN
    return Localization.getString(key, lang)
}

fun getLineHeight(viewModel: MainViewModel): TextUnit {
    // Return larger line height for Arabic to avoid clipping
    return if (viewModel.uiState.value.appLang == "ar") 20.sp else TextUnit.Unspecified
}
