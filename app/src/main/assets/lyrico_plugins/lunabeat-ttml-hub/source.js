'use strict';

(function () {
  var ROOT_URL = 'https://2755337087.github.io/ttml-hub';
  var API_URL = ROOT_URL + '/api/v1';
  var REVISION_CACHE_KEY = 'songs-revision-v2';
  var INDEX_CACHE_KEY = 'songs-index-v2';

  function normalize(value) {
    return String(value || '')
      .toLocaleLowerCase()
      .replace(/[\s_\-.,()[\]{}'"’“”·:：/\\]+/g, '');
  }

  function parseJson(raw, label) {
    try {
      return JSON.parse(raw);
    } catch (error) {
      throw new Error('Invalid LunaBeat ' + label + ': ' + error.message);
    }
  }

  function verifySha256(raw, expected, label) {
    if (!expected) return;
    var actual = Platform.crypto.sha256(raw);
    if (String(actual).toLowerCase() !== String(expected).toLowerCase()) {
      throw new Error('LunaBeat ' + label + ' SHA-256 mismatch');
    }
  }

  function loadIndex() {
    var cachedRevision = Platform.cache.get(REVISION_CACHE_KEY);
    var cachedRaw = Platform.cache.get(INDEX_CACHE_KEY);
    try {
      var manifestRaw = Platform.http.getText(API_URL + '/manifest.json');
      var manifest = parseJson(manifestRaw, 'manifest');
      if (Number(manifest.schemaVersion) !== 2) {
        throw new Error('Unsupported LunaBeat schemaVersion: ' + manifest.schemaVersion);
      }
      if (!cachedRaw || cachedRevision !== String(manifest.revision || '')) {
        var indexName = String(manifest.index || 'songs.json').replace(/^\/+/, '');
        var nextRaw = Platform.http.getText(API_URL + '/' + indexName);
        verifySha256(nextRaw, manifest.indexSha256, 'index');
        var nextIndex = parseJson(nextRaw, 'index');
        if (Number(nextIndex.schemaVersion) !== 2 || !Array.isArray(nextIndex.songs)) {
          throw new Error('Unsupported LunaBeat song index');
        }
        cachedRaw = nextRaw;
        cachedRevision = String(manifest.revision || nextIndex.revision || '');
        Platform.cache.set(INDEX_CACHE_KEY, cachedRaw);
        Platform.cache.set(REVISION_CACHE_KEY, cachedRevision);
      }
    } catch (error) {
      // An already verified index remains usable while GitHub Pages is temporarily unreachable.
      if (!cachedRaw) throw error;
      Platform.log.warn('LunaBeatTtmlHub', 'Using cached index: ' + error.message);
    }
    var index = parseJson(cachedRaw, 'cached index');
    return Array.isArray(index.songs) ? index.songs : [];
  }

  function aliasesOf(song) {
    var values = [song.title, song.album]
      .concat(Array.isArray(song.artists) ? song.artists : [])
      .concat(Array.isArray(song.aliases) ? song.aliases : [])
      .concat(Array.isArray(song.albums) ? song.albums : []);
    return values.map(normalize).filter(Boolean);
  }

  function matchScore(song, keyword) {
    var title = normalize(song.title);
    var artists = (Array.isArray(song.artists) ? song.artists : []).map(normalize);
    var albums = (Array.isArray(song.albums) ? song.albums : [song.album]).map(normalize);
    var aliases = (Array.isArray(song.aliases) ? song.aliases : []).map(normalize);
    if (title === keyword) return 1000;
    if (aliases.indexOf(keyword) >= 0) return 950;
    if (title.indexOf(keyword) === 0) return 850;
    if (title.indexOf(keyword) >= 0) return 780;
    if (aliases.some(function (value) { return value.indexOf(keyword) >= 0; })) return 720;
    if (artists.some(function (value) { return value.indexOf(keyword) >= 0; })) return 620;
    if (albums.some(function (value) { return value.indexOf(keyword) >= 0; })) return 520;
    if (aliasesOf(song).join('').indexOf(keyword) >= 0) return 420;
    return 0;
  }

  function firstSourceId(sourceIds) {
    if (!sourceIds || typeof sourceIds !== 'object') return '';
    var keys = ['appleMusicId', 'qqMusicId', 'ncmMusicId', 'isrc'];
    for (var i = 0; i < keys.length; i += 1) {
      var values = sourceIds[keys[i]];
      if (Array.isArray(values) && values.length) return String(values[0]);
    }
    return '';
  }

  function joinIds(sourceIds, key) {
    var values = sourceIds && sourceIds[key];
    return Array.isArray(values) ? values.map(String).join(',') : '';
  }

  globalThis.searchSongs = function (request) {
    var keyword = normalize(request && request.keyword);
    if (!keyword) return [];
    var page = Math.max(1, Number(request.page || 1));
    var pageSize = Math.max(1, Math.min(50, Number(request.pageSize || 20)));
    return loadIndex()
      .map(function (song) {
        return { song: song, score: matchScore(song, keyword) };
      })
      .filter(function (entry) { return entry.score > 0; })
      .sort(function (left, right) {
        return right.score - left.score || String(left.song.title).localeCompare(String(right.song.title));
      })
      .slice((page - 1) * pageSize, page * pageSize)
      .map(function (entry) {
        var song = entry.song;
        var sourceIds = song.sourceIds || {};
        return {
          id: String(song.id || ''),
          title: String(song.title || ''),
          artist: (Array.isArray(song.artists) ? song.artists : []).join(' / '),
          album: String(song.album || ''),
          duration: 0,
          sourceId: firstSourceId(sourceIds),
          fields: {
            language: String(song.language || ''),
            appleMusicId: joinIds(sourceIds, 'appleMusicId'),
            qqMusicId: joinIds(sourceIds, 'qqMusicId'),
            ncmMusicId: joinIds(sourceIds, 'ncmMusicId')
          },
          internal: {
            path: String(song.path || ''),
            sha256: String(song.sha256 || '')
          }
        };
      });
  };

  globalThis.getLyrics = function (request) {
    var song = request && request.song;
    var internal = song && song.internal;
    var path = internal && String(internal.path || '').replace(/^\/+/, '');
    if (!path || path.indexOf('..') >= 0) return null;
    var rawTtml = Platform.http.getText(ROOT_URL + '/' + path);
    verifySha256(rawTtml, internal.sha256, 'lyrics');
    return {
      type: 'rawTtml',
      rawTtml: rawTtml,
      tags: {
        ti: String(song.title || ''),
        ar: String(song.artist || ''),
        al: String(song.album || '')
      }
    };
  };
})();
