#!/usr/bin/env python3
"""Writes values*/strings.xml from one table, so a key can never be missing in one language."""
import os
from xml.sax.saxutils import escape

LANGS = ["en", "ru", "uk", "de", "fr", "es", "pt-rBR", "it", "pl", "tr", "nl", "ja", "ko", "zh-rCN"]

S = {
    "home": ["Home", "Главная", "Головна", "Start", "Accueil", "Inicio", "Início", "Home", "Start", "Ana sayfa", "Start", "ホーム", "홈", "主页"],
    "place_playlists": ["Playlists", "Плейлисты", "Плейлисти", "Playlists", "Playlists", "Listas", "Playlists", "Playlist", "Playlisty", "Çalma listeleri", "Afspeellijsten", "プレイリスト", "재생목록", "播放列表"],
    "place_artists": ["Artists", "Артисты", "Виконавці", "Künstler", "Artistes", "Artistas", "Artistas", "Artisti", "Wykonawcy", "Sanatçılar", "Artiesten", "アーティスト", "아티스트", "艺人"],
    "place_albums": ["Albums", "Альбомы", "Альбоми", "Alben", "Albums", "Álbumes", "Álbuns", "Album", "Albumy", "Albümler", "Albums", "アルバム", "앨범", "专辑"],
    "place_songs": ["Songs", "Песни", "Пісні", "Titel", "Titres", "Canciones", "Músicas", "Brani", "Utwory", "Şarkılar", "Nummers", "曲", "노래", "歌曲"],
    "place_recent": ["Recent", "Недавние", "Нещодавні", "Neu", "Récents", "Recientes", "Recentes", "Recenti", "Ostatnie", "Son eklenenler", "Recent", "最近", "최근", "最近添加"],
    "place_genres": ["Genres", "Жанры", "Жанри", "Genres", "Genres", "Géneros", "Gêneros", "Generi", "Gatunki", "Türler", "Genres", "ジャンル", "장르", "流派"],
    "search": ["Search", "Поиск", "Пошук", "Suchen", "Rechercher", "Buscar", "Buscar", "Cerca", "Szukaj", "Ara", "Zoeken", "検索", "검색", "搜索"],
    "search_hint": ["Songs, artists, albums", "Песни, артисты, альбомы", "Пісні, виконавці, альбоми", "Titel, Künstler, Alben", "Titres, artistes, albums", "Canciones, artistas, álbumes", "Músicas, artistas, álbuns", "Brani, artisti, album", "Utwory, wykonawcy, albumy", "Şarkılar, sanatçılar, albümler", "Nummers, artiesten, albums", "曲、アーティスト、アルバム", "노래, 아티스트, 앨범", "歌曲、艺人、专辑"],
    "results_none": ["Nothing found", "Ничего не найдено", "Нічого не знайдено", "Nichts gefunden", "Aucun résultat", "Sin resultados", "Nada encontrado", "Nessun risultato", "Nic nie znaleziono", "Sonuç yok", "Niets gevonden", "見つかりません", "결과 없음", "未找到"],
    "edit": ["Edit", "Изменить", "Змінити", "Bearbeiten", "Modifier", "Editar", "Editar", "Modifica", "Edytuj", "Düzenle", "Bewerken", "編集", "편집", "编辑"],
    "done": ["Done", "Готово", "Готово", "Fertig", "OK", "Listo", "Concluído", "Fine", "Gotowe", "Bitti", "Klaar", "完了", "완료", "完成"],
    "settings": ["Settings", "Настройки", "Налаштування", "Einstellungen", "Réglages", "Ajustes", "Configurações", "Impostazioni", "Ustawienia", "Ayarlar", "Instellingen", "設定", "설정", "设置"],
    "back": ["Back", "Назад", "Назад", "Zurück", "Retour", "Atrás", "Voltar", "Indietro", "Wstecz", "Geri", "Terug", "戻る", "뒤로", "返回"],
    "play": ["Play", "Играть", "Грати", "Abspielen", "Lecture", "Reproducir", "Tocar", "Riproduci", "Odtwórz", "Çal", "Afspelen", "再生", "재생", "播放"],
    "pause": ["Pause", "Пауза", "Пауза", "Pause", "Pause", "Pausa", "Pausar", "Pausa", "Pauza", "Duraklat", "Pauze", "一時停止", "일시정지", "暂停"],
    "shuffle": ["Shuffle", "Вперемешку", "Упереміш", "Zufällig", "Aléatoire", "Aleatorio", "Aleatório", "Casuale", "Losowo", "Karıştır", "Willekeurig", "シャッフル", "셔플", "随机"],
    "next": ["Next", "Следующий", "Наступний", "Weiter", "Suivant", "Siguiente", "Próxima", "Successivo", "Następny", "Sonraki", "Volgende", "次へ", "다음", "下一首"],
    "previous": ["Previous", "Предыдущий", "Попередній", "Zurück", "Précédent", "Anterior", "Anterior", "Precedente", "Poprzedni", "Önceki", "Vorige", "前へ", "이전", "上一首"],
    "repeat": ["Repeat", "Повтор", "Повтор", "Wiederholen", "Répéter", "Repetir", "Repetir", "Ripeti", "Powtarzaj", "Tekrarla", "Herhalen", "リピート", "반복", "重复"],
    "lyrics": ["Lyrics", "Текст", "Текст", "Liedtext", "Paroles", "Letra", "Letra", "Testo", "Tekst", "Sözler", "Songtekst", "歌詞", "가사", "歌词"],
    "queue": ["Queue", "Очередь", "Черга", "Warteschlange", "File d’attente", "Cola", "Fila", "Coda", "Kolejka", "Sıra", "Wachtrij", "キュー", "대기열", "队列"],
    "no_lyrics": ["No lyrics in this file", "В этом файле нет текста", "У цьому файлі немає тексту", "Kein Liedtext in dieser Datei", "Pas de paroles dans ce fichier", "Este archivo no tiene letra", "Este arquivo não tem letra", "Nessun testo in questo file", "Brak tekstu w tym pliku", "Bu dosyada söz yok", "Geen songtekst in dit bestand", "このファイルに歌詞はありません", "이 파일에는 가사가 없습니다", "此文件没有歌词"],
    "no_playlists": ["Playlists from your Mac library appear here once its database is in the chosen folder.", "Плейлисты с Mac появятся здесь, когда база медиатеки окажется в выбранной папке.", "Плейлисти з Mac з’являться тут, коли база медіатеки буде у вибраній теці.", "Playlists von deinem Mac erscheinen hier, sobald die Datenbank im gewählten Ordner liegt.", "Les playlists de votre Mac apparaîtront ici dès que la base sera dans le dossier choisi.", "Las listas de tu Mac aparecerán aquí cuando la base esté en la carpeta elegida.", "As playlists do seu Mac aparecem aqui quando o banco estiver na pasta escolhida.", "Le playlist del Mac appariranno qui quando il database sarà nella cartella scelta.", "Playlisty z Maca pojawią się tu, gdy baza znajdzie się w wybranym folderze.", "Mac çalma listeleri, veritabanı seçilen klasöre geldiğinde burada görünür.", "Afspeellijsten van je Mac verschijnen hier zodra de database in de gekozen map staat.", "データベースが選択したフォルダーにあると、Macのプレイリストがここに表示されます。", "데이터베이스가 선택한 폴더에 있으면 Mac 재생목록이 여기에 표시됩니다.", "数据库位于所选文件夹后，Mac 的播放列表会显示在这里。"],
    "hours_short": ["%1$d h", "%1$d ч", "%1$d год", "%1$d Std.", "%1$d h", "%1$d h", "%1$d h", "%1$d h", "%1$d godz.", "%1$d sa", "%1$d u", "%1$d時間", "%1$d시간", "%1$d 小时"],
    "sort_as_music": ["As in Music", "Как в Музыке", "Як у Музиці", "Wie in Musik", "Comme dans Musique", "Como en Música", "Como no Música", "Come in Musica", "Jak w Muzyce", "Müzik’teki gibi", "Zoals in Muziek", "ミュージックと同じ", "음악 앱과 동일", "与“音乐”相同"],
    "sort_artist": ["Artist", "Артист", "Виконавець", "Künstler", "Artiste", "Artista", "Artista", "Artista", "Wykonawca", "Sanatçı", "Artiest", "アーティスト", "아티스트", "艺人"],
    "sort_album": ["Album", "Альбом", "Альбом", "Album", "Album", "Álbum", "Álbum", "Album", "Album", "Albüm", "Album", "アルバム", "앨범", "专辑"],
    "sort_title": ["Title", "Название", "Назва", "Titel", "Titre", "Título", "Título", "Titolo", "Tytuł", "Ad", "Titel", "タイトル", "제목", "标题"],
    "sort_genre": ["Genre", "Жанр", "Жанр", "Genre", "Genre", "Género", "Gênero", "Genere", "Gatunek", "Tür", "Genre", "ジャンル", "장르", "流派"],
    "sort_date": ["Date added", "Дата добавления", "Дата додавання", "Hinzugefügt", "Date d’ajout", "Fecha de adición", "Data de adição", "Data di aggiunta", "Data dodania", "Eklenme tarihi", "Datum toegevoegd", "追加日", "추가한 날짜", "添加日期"],
    "sort_duration": ["Duration", "Длительность", "Тривалість", "Dauer", "Durée", "Duración", "Duração", "Durata", "Czas trwania", "Süre", "Duur", "長さ", "길이", "时长"],
    "sort_reverse": ["Reverse order", "Обратный порядок", "Зворотний порядок", "Umgekehrt", "Ordre inverse", "Orden inverso", "Ordem inversa", "Ordine inverso", "Odwrotna kolejność", "Ters sıra", "Omgekeerd", "逆順", "역순", "倒序"],
    "grant_title": ["Your music", "Твоя музыка", "Твоя музика", "Deine Musik", "Votre musique", "Tu música", "Sua música", "La tua musica", "Twoja muzyka", "Müziğin", "Je muziek", "あなたの音楽", "내 음악", "你的音乐"],
    "grant_text": ["Allow access to audio files to play them. Everything stays on this phone.", "Разреши доступ к аудиофайлам, чтобы их играть. Всё остаётся на этом телефоне.", "Дозволь доступ до аудіофайлів, щоб їх грати. Усе залишається на цьому телефоні.", "Erlaube den Zugriff auf Audiodateien, um sie abzuspielen. Alles bleibt auf diesem Telefon.", "Autorisez l’accès aux fichiers audio pour les lire. Tout reste sur ce téléphone.", "Permite el acceso a los archivos de audio para reproducirlos. Todo queda en este teléfono.", "Permita o acesso aos arquivos de áudio para tocá-los. Tudo fica neste celular.", "Consenti l’accesso ai file audio per riprodurli. Tutto resta su questo telefono.", "Zezwól na dostęp do plików audio, aby je odtwarzać. Wszystko zostaje na tym telefonie.", "Çalmak için ses dosyalarına erişime izin ver. Her şey bu telefonda kalır.", "Geef toegang tot audiobestanden om ze af te spelen. Alles blijft op deze telefoon.", "再生するためにオーディオファイルへのアクセスを許可してください。すべてこの端末内に留まります。", "재생하려면 오디오 파일 접근을 허용하세요. 모든 것은 이 휴대폰에 남습니다.", "允许访问音频文件以进行播放。所有内容都保留在此手机上。"],
    "grant_button": ["Allow", "Разрешить", "Дозволити", "Erlauben", "Autoriser", "Permitir", "Permitir", "Consenti", "Zezwól", "İzin ver", "Toestaan", "許可", "허용", "允许"],
    "folder_title": ["Music folder", "Папка Music", "Тека Music", "Ordner „Music“", "Dossier Music", "Carpeta Music", "Pasta Music", "Cartella Music", "Folder Music", "Music klasörü", "Map Music", "Music フォルダー", "Music 폴더", "Music 文件夹"],
    "folder_text": ["Pick the folder with your music. If it holds a copy of your Mac library, playlists and their order match the Mac.", "Выбери папку с музыкой. Если в ней есть копия медиатеки с Mac, плейлисты и их порядок совпадут с Mac.", "Вибери теку з музикою. Якщо в ній є копія медіатеки з Mac, плейлисти та їхній порядок збігатимуться з Mac.", "Wähle den Ordner mit deiner Musik. Liegt dort eine Kopie deiner Mac-Mediathek, stimmen Playlists und Reihenfolge mit dem Mac überein.", "Choisissez le dossier de musique. S’il contient une copie de la bibliothèque du Mac, les playlists et leur ordre seront identiques.", "Elige la carpeta con tu música. Si tiene una copia de la biblioteca del Mac, las listas y su orden coincidirán.", "Escolha a pasta com sua música. Se tiver uma cópia da biblioteca do Mac, as playlists e a ordem serão iguais.", "Scegli la cartella con la musica. Se contiene una copia della libreria del Mac, playlist e ordine coincideranno.", "Wybierz folder z muzyką. Jeśli zawiera kopię biblioteki z Maca, playlisty i ich kolejność będą takie same.", "Müzik klasörünü seç. İçinde Mac arşivinin kopyası varsa çalma listeleri ve sıraları Mac ile aynı olur.", "Kies de map met je muziek. Staat daar een kopie van je Mac-bibliotheek, dan komen afspeellijsten en volgorde overeen.", "音楽のフォルダーを選択します。Macのライブラリのコピーがあれば、プレイリストと順序がMacと一致します。", "음악 폴더를 선택하세요. Mac 보관함 사본이 있으면 재생목록과 순서가 Mac과 같아집니다.", "选择音乐文件夹。如果其中有 Mac 资料库的副本，播放列表及其顺序将与 Mac 一致。"],
    "folder_button": ["Choose folder", "Выбрать папку", "Вибрати теку", "Ordner wählen", "Choisir le dossier", "Elegir carpeta", "Escolher pasta", "Scegli cartella", "Wybierz folder", "Klasör seç", "Map kiezen", "フォルダーを選択", "폴더 선택", "选择文件夹"],
    "folder_skip": ["Use only the files on this phone", "Только файлы с телефона", "Лише файли з телефона", "Nur Dateien auf diesem Telefon", "Seulement les fichiers du téléphone", "Solo archivos del teléfono", "Só arquivos do celular", "Solo file del telefono", "Tylko pliki z telefonu", "Yalnızca telefondaki dosyalar", "Alleen bestanden op deze telefoon", "端末内のファイルのみ", "휴대폰 파일만 사용", "仅使用手机上的文件"],
    "folder_none": ["Not chosen", "Не выбрана", "Не вибрано", "Nicht gewählt", "Non choisi", "Sin elegir", "Não escolhida", "Non scelta", "Nie wybrano", "Seçilmedi", "Niet gekozen", "未選択", "선택 안 됨", "未选择"],
    "theme_poster": ["Poster", "Плакат", "Плакат", "Poster", "Affiche", "Póster", "Pôster", "Poster", "Plakat", "Afiş", "Poster", "ポスター", "포스터", "海报"],
    "theme_detailed": ["Detailed", "Детальная", "Детальна", "Detailliert", "Détaillé", "Detallado", "Detalhado", "Dettagliato", "Szczegółowy", "Detaylı", "Gedetailleerd", "ディテール", "디테일", "细节"],
    "theme_aero": ["Aero", "Аэро", "Аеро", "Aero", "Aero", "Aero", "Aero", "Aero", "Aero", "Aero", "Aero", "エアロ", "에어로", "Aero"],
    "s_appearance": ["Appearance", "Оформление", "Оформлення", "Darstellung", "Apparence", "Apariencia", "Aparência", "Aspetto", "Wygląd", "Görünüm", "Weergave", "外観", "모양", "外观"],
    "s_mode": ["Colours", "Цвета", "Кольори", "Farben", "Couleurs", "Colores", "Cores", "Colori", "Kolory", "Renkler", "Kleuren", "配色", "색상", "颜色"],
    "mode_system": ["As on the phone", "Как в телефоне", "Як у телефоні", "Wie im System", "Comme le téléphone", "Como el teléfono", "Como no celular", "Come il telefono", "Jak w telefonie", "Telefondaki gibi", "Zoals de telefoon", "端末と同じ", "휴대폰과 동일", "跟随手机"],
    "mode_dark": ["Dark", "Тёмные", "Темні", "Dunkel", "Sombre", "Oscuro", "Escuro", "Scuro", "Ciemny", "Koyu", "Donker", "ダーク", "어둡게", "深色"],
    "mode_black": ["Black", "Чёрные", "Чорні", "Schwarz", "Noir", "Negro", "Preto", "Nero", "Czarny", "Siyah", "Zwart", "ブラック", "검정", "黑色"],
    "mode_light": ["Light", "Светлые", "Світлі", "Hell", "Clair", "Claro", "Claro", "Chiaro", "Jasny", "Açık", "Licht", "ライト", "밝게", "浅色"],
    "s_cover_color": ["Player in cover colour", "Плеер в цвете обложки", "Плеєр у кольорі обкладинки", "Player in Coverfarbe", "Lecteur couleur pochette", "Reproductor color portada", "Player na cor da capa", "Player col colore copertina", "Odtwarzacz w kolorze okładki", "Kapak renginde oynatıcı", "Speler in hoeskleur", "ジャケット色のプレーヤー", "커버 색상 플레이어", "播放器使用封面颜色"],
    "s_cover_color_hint": ["The colour changes with each track", "Цвет меняется с каждым треком", "Колір змінюється з кожним треком", "Die Farbe wechselt mit jedem Titel", "La couleur change à chaque titre", "El color cambia con cada canción", "A cor muda a cada música", "Il colore cambia a ogni brano", "Kolor zmienia się z każdym utworem", "Renk her parçayla değişir", "De kleur wisselt per nummer", "曲ごとに色が変わります", "곡마다 색이 바뀝니다", "颜色随歌曲变化"],
    "s_language": ["Language", "Язык", "Мова", "Sprache", "Langue", "Idioma", "Idioma", "Lingua", "Język", "Dil", "Taal", "言語", "언어", "语言"],
    "lang_system": ["As on the phone", "Как в телефоне", "Як у телефоні", "Wie im System", "Comme le téléphone", "Como el teléfono", "Como no celular", "Come il telefono", "Jak w telefonie", "Telefondaki gibi", "Zoals de telefoon", "端末と同じ", "휴대폰과 동일", "跟随手机"],
    "s_library": ["Library", "Медиатека", "Медіатека", "Mediathek", "Bibliothèque", "Biblioteca", "Biblioteca", "Libreria", "Biblioteka", "Arşiv", "Bibliotheek", "ライブラリ", "보관함", "资料库"],
    "s_source": ["Source", "Источник", "Джерело", "Quelle", "Source", "Origen", "Origem", "Origine", "Źródło", "Kaynak", "Bron", "ソース", "소스", "来源"],
    "s_status_db": ["Mac library", "Медиатека Mac", "Медіатека Mac", "Mac-Mediathek", "Bibliothèque du Mac", "Biblioteca del Mac", "Biblioteca do Mac", "Libreria del Mac", "Biblioteka Maca", "Mac arşivi", "Mac-bibliotheek", "Macのライブラリ", "Mac 보관함", "Mac 资料库"],
    "s_status_device": ["Files on this phone", "Файлы на телефоне", "Файли на телефоні", "Dateien auf dem Telefon", "Fichiers du téléphone", "Archivos del teléfono", "Arquivos do celular", "File del telefono", "Pliki na telefonie", "Telefondaki dosyalar", "Bestanden op telefoon", "端末内のファイル", "휴대폰 파일", "手机上的文件"],
    "s_status_failed": ["The library could not be read, showing files on this phone", "Не удалось прочитать медиатеку, показаны файлы с телефона", "Не вдалося прочитати медіатеку, показано файли з телефона", "Mediathek nicht lesbar, Dateien des Telefons werden gezeigt", "Bibliothèque illisible, fichiers du téléphone affichés", "No se pudo leer la biblioteca, se muestran archivos del teléfono", "Não foi possível ler a biblioteca; mostrando arquivos do celular", "Libreria illeggibile, mostro i file del telefono", "Nie można odczytać biblioteki, pokazano pliki z telefonu", "Arşiv okunamadı, telefondaki dosyalar gösteriliyor", "Bibliotheek onleesbaar, bestanden op telefoon getoond", "ライブラリを読めないため端末内のファイルを表示しています", "보관함을 읽을 수 없어 휴대폰 파일을 표시합니다", "无法读取资料库，显示手机上的文件"],
    "s_missing": ["Not on this phone", "Нет на телефоне", "Немає на телефоні", "Nicht auf dem Telefon", "Absents du téléphone", "No están en el teléfono", "Não estão no celular", "Non sul telefono", "Nie ma na telefonie", "Telefonda yok", "Niet op telefoon", "端末にありません", "휴대폰에 없음", "不在手机上"],
    "s_folder": ["Music folder", "Папка с музыкой", "Тека з музикою", "Musikordner", "Dossier de musique", "Carpeta de música", "Pasta de música", "Cartella musica", "Folder z muzyką", "Müzik klasörü", "Muziekmap", "音楽フォルダー", "음악 폴더", "音乐文件夹"],
    "s_update": ["Read the library again", "Перечитать медиатеку", "Перечитати медіатеку", "Mediathek neu lesen", "Relire la bibliothèque", "Volver a leer la biblioteca", "Ler a biblioteca de novo", "Rileggi la libreria", "Wczytaj bibliotekę ponownie", "Arşivi yeniden oku", "Bibliotheek opnieuw lezen", "ライブラリを再読み込み", "보관함 다시 읽기", "重新读取资料库"],
    "s_playback": ["Playback", "Воспроизведение", "Відтворення", "Wiedergabe", "Lecture", "Reproducción", "Reprodução", "Riproduzione", "Odtwarzanie", "Oynatma", "Afspelen", "再生", "재생", "播放"],
    "s_skip_silence": ["Skip silence", "Пропускать тишину", "Пропускати тишу", "Stille überspringen", "Ignorer les silences", "Omitir silencios", "Pular silêncio", "Salta i silenzi", "Pomijaj ciszę", "Sessizliği atla", "Stilte overslaan", "無音をスキップ", "무음 건너뛰기", "跳过静音"],
    "s_speed": ["Speed", "Скорость", "Швидкість", "Tempo", "Vitesse", "Velocidad", "Velocidade", "Velocità", "Prędkość", "Hız", "Snelheid", "速度", "속도", "速度"],
    "s_sleep": ["Sleep timer", "Таймер сна", "Таймер сну", "Schlaftimer", "Minuteur", "Temporizador", "Temporizador", "Timer", "Wyłącznik czasowy", "Uyku zamanlayıcısı", "Slaaptimer", "スリープタイマー", "수면 타이머", "睡眠定时"],
    "sleep_off": ["Off", "Выключен", "Вимкнено", "Aus", "Désactivé", "Desactivado", "Desligado", "Disattivato", "Wyłączony", "Kapalı", "Uit", "オフ", "끔", "关闭"],
    "sleep_min": ["%1$d min", "%1$d мин", "%1$d хв", "%1$d Min.", "%1$d min", "%1$d min", "%1$d min", "%1$d min", "%1$d min", "%1$d dk", "%1$d min", "%1$d分", "%1$d분", "%1$d 分钟"],
    "s_lyrics_size": ["Text size", "Размер текста", "Розмір тексту", "Textgröße", "Taille du texte", "Tamaño del texto", "Tamanho do texto", "Dimensione testo", "Rozmiar tekstu", "Metin boyutu", "Tekstgrootte", "文字サイズ", "글자 크기", "文字大小"],
    "size_small": ["Small", "Мелкий", "Дрібний", "Klein", "Petit", "Pequeño", "Pequeno", "Piccolo", "Mały", "Küçük", "Klein", "小", "작게", "小"],
    "size_medium": ["Medium", "Средний", "Середній", "Mittel", "Moyen", "Medio", "Médio", "Medio", "Średni", "Orta", "Middel", "中", "보통", "中"],
    "size_large": ["Large", "Крупный", "Великий", "Groß", "Grand", "Grande", "Grande", "Grande", "Duży", "Büyük", "Groot", "大", "크게", "大"],
    "s_keep_screen": ["Keep screen on with lyrics", "Не гасить экран с текстом", "Не вимикати екран із текстом", "Bildschirm bei Liedtext anlassen", "Garder l’écran allumé", "Pantalla encendida con letra", "Manter a tela ligada com a letra", "Schermo acceso con il testo", "Nie wygaszaj ekranu przy tekście", "Sözlerde ekran açık kalsın", "Scherm aan bij songtekst", "歌詞表示中は画面をオン", "가사 표시 중 화면 켜짐", "显示歌词时保持屏幕常亮"],
    "s_lyric_popup": ["Current line over the mini player", "Строка текста над мини-плеером", "Рядок тексту над міні-плеєром", "Aktuelle Zeile über dem Miniplayer", "Ligne en cours au-dessus du mini-lecteur", "Línea actual sobre el minirreproductor", "Linha atual sobre o miniplayer", "Riga corrente sopra il mini player", "Bieżący wiersz nad minipanelem", "Mini oynatıcının üstünde geçerli satır", "Huidige regel boven de minispeler", "ミニプレーヤーの上に現在の行", "미니 플레이어 위에 현재 가사", "在迷你播放器上方显示当前歌词"],
    "no_db_title": ["No Mac library in this folder", "В этой папке нет медиатеки Mac", "У цій теці немає медіатеки Mac", "Keine Mac-Mediathek in diesem Ordner", "Pas de bibliothèque Mac dans ce dossier", "No hay biblioteca del Mac en esta carpeta", "Não há biblioteca do Mac nesta pasta", "Nessuna libreria Mac in questa cartella", "W tym folderze nie ma biblioteki Maca", "Bu klasörde Mac arşivi yok", "Geen Mac-bibliotheek in deze map", "このフォルダーにMacのライブラリはありません", "이 폴더에 Mac 보관함이 없습니다", "此文件夹中没有 Mac 资料库"],
    "s_online_art": ["Find missing covers online", "Искать недостающие обложки в сети", "Шукати відсутні обкладинки в мережі", "Fehlende Cover online suchen", "Chercher les pochettes manquantes en ligne", "Buscar portadas que faltan en línea", "Buscar capas que faltam on-line", "Cerca online le copertine mancanti", "Szukaj brakujących okładek w sieci", "Eksik kapakları internette ara", "Ontbrekende hoezen online zoeken", "足りないジャケットをオンラインで探す", "없는 커버를 온라인에서 찾기", "在线查找缺失的封面"],
    "s_online_art_hint": ["Sends the artist and title of tracks without a picture to Deezer and iTunes", "Отправляет артиста и название треков без картинки в Deezer и iTunes", "Надсилає виконавця й назву треків без зображення до Deezer та iTunes", "Sendet Künstler und Titel von Tracks ohne Bild an Deezer und iTunes", "Envoie l’artiste et le titre des morceaux sans image à Deezer et iTunes", "Envía el artista y el título de las canciones sin imagen a Deezer e iTunes", "Envia artista e título das músicas sem imagem ao Deezer e ao iTunes", "Invia artista e titolo dei brani senza immagine a Deezer e iTunes", "Wysyła wykonawcę i tytuł utworów bez obrazu do Deezer i iTunes", "Görseli olmayan parçaların sanatçı ve adını Deezer ve iTunes’a gönderir", "Stuurt artiest en titel van nummers zonder afbeelding naar Deezer en iTunes", "画像のない曲のアーティスト名と曲名をDeezerとiTunesに送信します", "이미지가 없는 곡의 아티스트와 제목을 Deezer 및 iTunes로 보냅니다", "将无图片歌曲的艺人和标题发送至 Deezer 和 iTunes"],
    "s_lyrics_lead": ["Show lines ahead by", "Показывать строку заранее", "Показувати рядок заздалегідь", "Zeile früher anzeigen", "Afficher la ligne en avance", "Mostrar la línea antes", "Mostrar a linha antes", "Mostra la riga in anticipo", "Pokazuj wiersz wcześniej", "Satırı önceden göster", "Regel eerder tonen", "行を早めに表示", "가사 미리 표시", "提前显示歌词"],
    "seconds_short": ["%1$.2f s", "%1$.2f с", "%1$.2f с", "%1$.2f s", "%1$.2f s", "%1$.2f s", "%1$.2f s", "%1$.2f s", "%1$.2f s", "%1$.2f sn", "%1$.2f s", "%1$.2f秒", "%1$.2f초", "%1$.2f 秒"],
    "s_about": ["About", "О приложении", "Про застосунок", "Über", "À propos", "Acerca de", "Sobre", "Informazioni", "O aplikacji", "Hakkında", "Over", "このアプリについて", "정보", "关于"],
    "s_privacy": ["Privacy policy", "Политика конфиденциальности", "Політика конфіденційності", "Datenschutz", "Confidentialité", "Privacidad", "Privacidade", "Privacy", "Prywatność", "Gizlilik", "Privacy", "プライバシー", "개인정보처리방침", "隐私政策"],
}

SONGS = {
    "en": dict(one="%d song", other="%d songs"),
    "ru": dict(one="%d песня", few="%d песни", many="%d песен", other="%d песни"),
    "uk": dict(one="%d пісня", few="%d пісні", many="%d пісень", other="%d пісні"),
    "de": dict(one="%d Titel", other="%d Titel"),
    "fr": dict(one="%d titre", other="%d titres"),
    "es": dict(one="%d canción", other="%d canciones"),
    "pt-rBR": dict(one="%d música", other="%d músicas"),
    "it": dict(one="%d brano", other="%d brani"),
    "pl": dict(one="%d utwór", few="%d utwory", many="%d utworów", other="%d utworu"),
    "tr": dict(one="%d şarkı", other="%d şarkı"),
    "nl": dict(one="%d nummer", other="%d nummers"),
    "ja": dict(other="%d曲"),
    "ko": dict(other="%d곡"),
    "zh-rCN": dict(other="%d 首歌曲"),
}
ALBUMS = {
    "en": dict(one="%d album", other="%d albums"),
    "ru": dict(one="%d альбом", few="%d альбома", many="%d альбомов", other="%d альбома"),
    "uk": dict(one="%d альбом", few="%d альбоми", many="%d альбомів", other="%d альбому"),
    "de": dict(one="%d Album", other="%d Alben"),
    "fr": dict(one="%d album", other="%d albums"),
    "es": dict(one="%d álbum", other="%d álbumes"),
    "pt-rBR": dict(one="%d álbum", other="%d álbuns"),
    "it": dict(one="%d album", other="%d album"),
    "pl": dict(one="%d album", few="%d albumy", many="%d albumów", other="%d albumu"),
    "tr": dict(one="%d albüm", other="%d albüm"),
    "nl": dict(one="%d album", other="%d albums"),
    "ja": dict(other="%dアルバム"),
    "ko": dict(other="앨범 %d개"),
    "zh-rCN": dict(other="%d 张专辑"),
}


def esc(s):
    return escape(s).replace("'", "\\'").replace('"', '\\"')


def main():
    root = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
    for i, lang in enumerate(LANGS):
        d = os.path.join(root, "values" if lang == "en" else f"values-{lang}")
        os.makedirs(d, exist_ok=True)
        out = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
        if lang == "en":
            out.append('    <string name="app_name" translatable="false">Muz On</string>')
        for key, values in S.items():
            assert len(values) == len(LANGS), key
            out.append(f'    <string name="{key}">{esc(values[i])}</string>')
        for name, table in (("songs", SONGS), ("albums", ALBUMS)):
            out.append(f'    <plurals name="{name}">')
            for q, text in table[lang].items():
                out.append(f'        <item quantity="{q}">{esc(text)}</item>')
            out.append("    </plurals>")
        out.append("</resources>\n")
        open(os.path.join(d, "strings.xml"), "w", encoding="utf-8").write("\n".join(out))
    xml = ['<?xml version="1.0" encoding="utf-8"?>', '<locale-config xmlns:android="http://schemas.android.com/apk/res/android">']
    for lang in LANGS:
        xml.append(f'    <locale android:name="{lang.replace("-r", "-")}" />')
    xml.append("</locale-config>\n")
    os.makedirs(os.path.join(root, "xml"), exist_ok=True)
    open(os.path.join(root, "xml", "locales_config.xml"), "w", encoding="utf-8").write("\n".join(xml))


main()
