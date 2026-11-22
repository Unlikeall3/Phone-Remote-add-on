[README.md](https://github.com/user-attachments/files/35020128/README.md)
# Phone Remote for Kodi / CoreELEC / LibreELEC

**Control Kodi from any phone or laptop browser — no app to install.**
Phone Remote is a Kodi service add-on that starts a small web server (default port `8090`) and serves a mobile-friendly web page: file browser, playback, remote control, torrents, system tools, and the ability to **stream files straight from the device your browser runs on**.

> Tested on CoreELEC 22 / Kodi 22 (Amlogic) and LibreELEC (x86), with Chrome and Firefox on Android and desktop.

## Features

**Playback & library**
- Browse local folders and network sources (SMB, NFS, FTP/SFTP, WebDAV, UPnP, HTTP) — with a *Browse* picker like Kodi's "Add source"
- Play queue with drag-to-reorder, shuffle/repeat, saved playlists, resume points and "Continue watching" with covers
- Search and a catalog of all your files (any file type) with covers; clear / refresh catalog
- Audio and subtitle track selection, seek, volume, HDMI-CEC volume

**Remote control**
- On-screen remote buttons, text input, macros (key / window / add-on / play / wait steps)
- Customizable **Quick Access** shortcuts (folder, file, plugin path, terminal command, remote button, macro, Kodi window) with path pickers
- Home screen as tiles or a drop-down menu; English / Russian UI

**Share from this device**
- Pick files or a folder in your phone/PC browser and play them on the TV box — the box pulls the data through the open page
- Separate relay process (independent of Kodi's embedded Python), read-ahead buffer with prebuffering and auto-pause on buffer underrun
- Optional copy-to-box

**Video statistics**
- Live video/audio/HDR/Dolby Vision statistics (via the TinyPPI add-on data feed when available) shown on the TV, in the browser, or both

**Torrents & system**
- Sequential torrent download with "watch while downloading", file picker, hash recheck
- qBittorrent installer, Wake-on-LAN, power menu, sleep timer, system status
- Restart Kodi without powering off the TV (no CEC standby), event log and one-click debug dump
- Auto-confirm dialogs (optional, off by default), follow-to-player option

## Install

1. Download the latest `service.phoneremote-<version>.zip` from the [Releases](../../releases) page.
2. In Kodi: **Settings → Add-ons → Install from zip file** and select the file (enable "Unknown sources" if asked).
3. The service starts automatically. Open `http://<box-ip>:8090/` in a browser on the same network (the IP is also shown in a Kodi notification at startup).

## Usage tips

- Add the page to your phone's home screen for a one-tap remote.
- Set a PIN in the add-on settings if the network is not fully trusted — the page and API require it.
- **Share from this device:** *Files → Share this device*, choose files, then play them from the *Shared* source. Keep the browser tab open while playing. A wired connection on the box and a strong Wi-Fi signal on the phone give the best results for high-bitrate 4K files.

## Troubleshooting

- Use *Settings → Debug* in the web page to download a debug dump (browser log, add-on events, share-process log, Kodi log).
- The share relay log is written to `sharesrv.log` in the add-on's data folder; it also contains a "freeze recorder" that captures system load and blocked kernel tasks if the box stalls.

## Repository layout

```
service.phoneremote/
  addon.xml         add-on metadata
  service.py        Kodi service: HTTP server, API, queue, catalog, torrents
  sharecore.py      share-from-device core (buffering, prefetch, serving)
  sharesrv.py       separate relay process for shared files + freeze recorder
  remote.py, installer.py, instjob.py, qbctl.py
  web/index.html    single-page mobile UI
  resources/        settings, language strings, qBittorrent setup script
```

## Build

```sh
./build.sh          # creates dist/service.phoneremote-<version>.zip
```

The zip must contain the `service.phoneremote/` folder at its root.

## Contributing

Issues and pull requests are welcome. When reporting a bug please attach the debug dump and your Kodi version / OS (CoreELEC, LibreELEC, …).

## License

[MIT](LICENSE)

## Support the developer

Roman — subscribe to the YouTube channel: https://youtube.com/@unlikeall3

Tutorial how to install Phone Remote as app from google chrome on android(probably for many other browsers you can install it without this) also make shure that in app settings show nearby devices enabled,otherwise you won't open the page.

Enter this in adress bar:

chrome://flags/#unsafely-treat-insecure-origin-as-secure

Insecure origins treated as secure
Treat given (insecure) origins as secure origins.

Enter Phone remote IP in field below,for me it is:
http://192.168.50.196:8090

And tap:
#unsafely-treat-insecure-origin-as-secure

Now you can install it as app in google chrome,click on 3 tods,scroll down and click install as app.It should appear as app on your home screen.

You will get ability to share links straight into Phone Remote and play them.
