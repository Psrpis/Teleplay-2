import express from 'express';
import cors from 'cors';
import path from 'path';
import { createServer as createViteServer } from 'vite';

// Types aligned with TelePlay frontend
interface User {
  id: number;
  telegram_id: number;
  username: string | null;
  first_name: string | null;
  last_name: string | null;
  created_at: string;
  last_active: string;
}

interface Folder {
  id: number;
  name: string;
  parent_id: number | null;
  user_id: number;
  created_at: string;
  updated_at: string;
  file_count: number;
  children?: Folder[];
}

interface MediaMetadata {
  title?: string | null;
  original_title?: string | null;
  overview?: string | null;
  year?: number | null;
  runtime?: number | null;
  genres?: string[];
  rating?: number | null;
  poster_url?: string | null;
  backdrop_url?: string | null;
  cast?: string[];
  directors?: string[];
  external_id?: string | null;
  media_type?: string | null;
  season?: number | null;
  episode?: number | null;
  provider?: string | null;
}

interface TelegramFile {
  id: number;
  user_id: number;
  folder_id: number | null;
  file_id: string;
  file_unique_id: string;
  file_name: string;
  file_size: number;
  mime_type: string | null;
  file_type: 'video' | 'audio' | 'document' | 'image';
  duration: number | null;
  width: number | null;
  height: number | null;
  created_at: string;
  updated_at: string;
  stream_url: string;
  thumbnail_url: string | null;
  last_pos?: number;
  public_hash?: string;
  public_stream_url?: string;
  progress_percent?: number;
  watched_state?: 'unwatched' | 'in_progress' | 'watched';
  is_favorite?: boolean;
  last_watched?: string | null;
  metadata?: MediaMetadata | null;
  tags?: string[];
}

interface Collection {
  id: number;
  name: string;
  description?: string | null;
  created_at: string;
  updated_at: string;
  item_count: number;
  files?: TelegramFile[];
}

interface HistoryEntry {
  id: number;
  file_id: number;
  watched_at: string;
  position?: number | null;
  duration?: number | null;
  file?: TelegramFile | null;
}

// In-Memory Database
const demoUser: User = {
  id: 1,
  telegram_id: 123456789,
  username: 'teleplay_demo',
  first_name: 'TelePlay',
  last_name: 'User',
  created_at: '2024-01-01T00:00:00Z',
  last_active: new Date().toISOString(),
};

let activeLoginCodes: Map<string, { expires: number; verified: boolean }> = new Map();

let folders: Folder[] = [
  {
    id: 1,
    name: 'Movies',
    parent_id: null,
    user_id: 1,
    created_at: new Date(Date.now() - 86400000 * 10).toISOString(),
    updated_at: new Date().toISOString(),
    file_count: 3,
  },
  {
    id: 2,
    name: 'TV Shows',
    parent_id: null,
    user_id: 1,
    created_at: new Date(Date.now() - 86400000 * 8).toISOString(),
    updated_at: new Date().toISOString(),
    file_count: 2,
  },
  {
    id: 3,
    name: 'Music & Audio',
    parent_id: null,
    user_id: 1,
    created_at: new Date(Date.now() - 86400000 * 5).toISOString(),
    updated_at: new Date().toISOString(),
    file_count: 1,
  },
  {
    id: 4,
    name: 'Animations',
    parent_id: 1,
    user_id: 1,
    created_at: new Date(Date.now() - 86400000 * 4).toISOString(),
    updated_at: new Date().toISOString(),
    file_count: 2,
  },
];

let files: TelegramFile[] = [
  {
    id: 1,
    user_id: 1,
    folder_id: 1,
    file_id: 'tg_file_001',
    file_unique_id: 'uniq_001',
    file_name: 'Big_Buck_Bunny_1080p.mp4',
    file_size: 276134947,
    mime_type: 'video/mp4',
    file_type: 'video',
    duration: 596,
    width: 1920,
    height: 1080,
    created_at: new Date(Date.now() - 86400000 * 3).toISOString(),
    updated_at: new Date().toISOString(),
    stream_url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
    thumbnail_url: 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=800&auto=format&fit=crop&q=60',
    last_pos: 240,
    progress_percent: 40,
    watched_state: 'in_progress',
    is_favorite: true,
    last_watched: new Date(Date.now() - 3600000 * 2).toISOString(),
    metadata: {
      title: 'Big Buck Bunny',
      original_title: 'Big Buck Bunny',
      overview: 'A large and lovable rabbit deals with bullying forest creatures in this iconic open-source animated adventure.',
      year: 2008,
      runtime: 10,
      genres: ['Animation', 'Comedy', 'Family'],
      rating: 7.9,
      poster_url: 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=800&auto=format&fit=crop&q=60',
      backdrop_url: 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=1600&auto=format&fit=crop&q=80',
      directors: ['Sacha Goedegebure'],
      cast: ['Bunny', 'Frank', 'Rinky', 'Gimera'],
      media_type: 'movie',
    },
    tags: ['Animation', '1080p', 'Short', 'Blender'],
  },
  {
    id: 2,
    user_id: 1,
    folder_id: 1,
    file_id: 'tg_file_002',
    file_unique_id: 'uniq_002',
    file_name: 'Tears_of_Steel_4K.mp4',
    file_size: 562144000,
    mime_type: 'video/mp4',
    file_type: 'video',
    duration: 734,
    width: 3840,
    height: 2160,
    created_at: new Date(Date.now() - 86400000 * 2).toISOString(),
    updated_at: new Date().toISOString(),
    stream_url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4',
    thumbnail_url: 'https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=800&auto=format&fit=crop&q=60',
    last_pos: 0,
    progress_percent: 0,
    watched_state: 'unwatched',
    is_favorite: true,
    metadata: {
      title: 'Tears of Steel',
      original_title: 'Tears of Steel',
      overview: 'Set in a dystopian future Amsterdam, a squad of resistance fighters and scientists attempt to stage a memory broadcast.',
      year: 2012,
      runtime: 12,
      genres: ['Sci-Fi', 'Action'],
      rating: 7.2,
      poster_url: 'https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=800&auto=format&fit=crop&q=60',
      backdrop_url: 'https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=1600&auto=format&fit=crop&q=80',
      directors: ['Ian Hubert'],
      cast: ['Derek de Lint', 'Sergio Hasselbaink'],
      media_type: 'movie',
    },
    tags: ['Sci-Fi', '4K', 'VFX'],
  },
  {
    id: 3,
    user_id: 1,
    folder_id: 1,
    file_id: 'tg_file_003',
    file_unique_id: 'uniq_003',
    file_name: 'Sintel_The_Dragon_Girl.mp4',
    file_size: 412000000,
    mime_type: 'video/mp4',
    file_type: 'video',
    duration: 888,
    width: 1920,
    height: 1080,
    created_at: new Date(Date.now() - 86400000 * 5).toISOString(),
    updated_at: new Date().toISOString(),
    stream_url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4',
    thumbnail_url: 'https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&auto=format&fit=crop&q=60',
    last_pos: 888,
    progress_percent: 100,
    watched_state: 'watched',
    is_favorite: false,
    last_watched: new Date(Date.now() - 86400000 * 1).toISOString(),
    metadata: {
      title: 'Sintel',
      original_title: 'Sintel',
      overview: 'A lonely young woman searches relentlessly across snowy peaks and desolate lands for a baby dragon she befriended.',
      year: 2010,
      runtime: 15,
      genres: ['Fantasy', 'Adventure', 'Animation'],
      rating: 8.1,
      poster_url: 'https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&auto=format&fit=crop&q=60',
      backdrop_url: 'https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1600&auto=format&fit=crop&q=80',
      directors: ['Colin Levy'],
      cast: ['Halina Reijn', 'Thom Hoffman'],
      media_type: 'movie',
    },
    tags: ['Fantasy', 'Adventure', 'Blender'],
  },
  {
    id: 4,
    user_id: 1,
    folder_id: 2,
    file_id: 'tg_file_004',
    file_unique_id: 'uniq_004',
    file_name: 'Cosmos_S01E01_Standing_Up_in_the_Milky_Way.mp4',
    file_size: 780000000,
    mime_type: 'video/mp4',
    file_type: 'video',
    duration: 2640,
    width: 1920,
    height: 1080,
    created_at: new Date(Date.now() - 86400000 * 6).toISOString(),
    updated_at: new Date().toISOString(),
    stream_url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4',
    thumbnail_url: 'https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800&auto=format&fit=crop&q=60',
    last_pos: 1200,
    progress_percent: 45,
    watched_state: 'in_progress',
    is_favorite: true,
    last_watched: new Date(Date.now() - 3600000 * 5).toISOString(),
    metadata: {
      title: 'Cosmos: A Spacetime Odyssey',
      original_title: 'Standing Up in the Milky Way',
      overview: 'An exploration of our address in the universe and the Cosmic Calendar.',
      year: 2014,
      runtime: 44,
      genres: ['Documentary', 'Science'],
      rating: 9.3,
      poster_url: 'https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800&auto=format&fit=crop&q=60',
      backdrop_url: 'https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=1600&auto=format&fit=crop&q=80',
      directors: ['Brannon Braga'],
      cast: ['Neil deGrasse Tyson'],
      media_type: 'tv',
      season: 1,
      episode: 1,
    },
    tags: ['Cosmos', 'Documentary', 'Science', 'S01E01'],
  },
  {
    id: 5,
    user_id: 1,
    folder_id: 2,
    file_id: 'tg_file_005',
    file_unique_id: 'uniq_005',
    file_name: 'Cosmos_S01E02_Some_of_the_Things_That_Molecules_Do.mp4',
    file_size: 790000000,
    mime_type: 'video/mp4',
    file_type: 'video',
    duration: 2640,
    width: 1920,
    height: 1080,
    created_at: new Date(Date.now() - 86400000 * 7).toISOString(),
    updated_at: new Date().toISOString(),
    stream_url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4',
    thumbnail_url: 'https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=800&auto=format&fit=crop&q=60',
    last_pos: 0,
    progress_percent: 0,
    watched_state: 'unwatched',
    is_favorite: false,
    metadata: {
      title: 'Cosmos: A Spacetime Odyssey',
      original_title: 'Some of the Things That Molecules Do',
      overview: 'An exploration of evolution and the complexity of life on Earth.',
      year: 2014,
      runtime: 44,
      genres: ['Documentary', 'Science'],
      rating: 9.1,
      poster_url: 'https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=800&auto=format&fit=crop&q=60',
      backdrop_url: 'https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=1600&auto=format&fit=crop&q=80',
      directors: ['Bill Pope'],
      cast: ['Neil deGrasse Tyson'],
      media_type: 'tv',
      season: 1,
      episode: 2,
    },
    tags: ['Cosmos', 'Documentary', 'Science', 'S01E02'],
  },
  {
    id: 6,
    user_id: 1,
    folder_id: 3,
    file_id: 'tg_file_006',
    file_unique_id: 'uniq_006',
    file_name: 'Chill_Synthwave_Nights.mp3',
    file_size: 8520000,
    mime_type: 'audio/mpeg',
    file_type: 'audio',
    duration: 215,
    width: null,
    height: null,
    created_at: new Date(Date.now() - 86400000 * 1).toISOString(),
    updated_at: new Date().toISOString(),
    stream_url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4',
    thumbnail_url: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&auto=format&fit=crop&q=60',
    last_pos: 0,
    progress_percent: 0,
    watched_state: 'unwatched',
    is_favorite: false,
    metadata: {
      title: 'Chill Synthwave Nights',
      genres: ['Electronic', 'Synthwave'],
      year: 2024,
    },
    tags: ['Electronic', 'Audio', 'Synthwave'],
  },
];

let collections: Collection[] = [
  {
    id: 1,
    name: 'Staff Picks & Classics',
    description: 'Essential open-source animated films and shorts',
    created_at: new Date(Date.now() - 86400000 * 5).toISOString(),
    updated_at: new Date().toISOString(),
    item_count: 2,
    files: [files[0], files[1]],
  },
  {
    id: 2,
    name: 'Cosmos Marathon',
    description: 'Episodes from Neil deGrasse Tyson space series',
    created_at: new Date(Date.now() - 86400000 * 3).toISOString(),
    updated_at: new Date().toISOString(),
    item_count: 2,
    files: [files[3], files[4]],
  },
];

let watchHistory: HistoryEntry[] = [
  {
    id: 1,
    file_id: 1,
    watched_at: new Date(Date.now() - 3600000 * 2).toISOString(),
    position: 240,
    duration: 596,
    file: files[0],
  },
  {
    id: 2,
    file_id: 4,
    watched_at: new Date(Date.now() - 3600000 * 5).toISOString(),
    position: 1200,
    duration: 2640,
    file: files[3],
  },
  {
    id: 3,
    file_id: 3,
    watched_at: new Date(Date.now() - 86400000 * 1).toISOString(),
    position: 888,
    duration: 888,
    file: files[2],
  },
];

let preferences: Record<string, string> = {
  theme: 'dark',
  audio_language: 'en',
  subtitles: 'off',
  default_player: 'internal',
};

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(cors());
  app.use(express.json());

  // API router
  const apiRouter = express.Router();

  // Health check
  apiRouter.get('/health', (req, res) => {
    res.json({ status: 'healthy' });
  });

  // Auth: Bot Info
  apiRouter.get('/auth/bot/info', (req, res) => {
    res.json({
      username: 'TelePlayStreamBot',
      name: 'TelePlay Stream Bot',
      server_version: '1.3.1',
    });
  });

  // Auth: Generate Login Code
  apiRouter.post('/auth/generate-code', (req, res) => {
    // Generate 6 digit code
    const code = Math.floor(100000 + Math.random() * 900000).toString();
    activeLoginCodes.set(code, {
      expires: Date.now() + 600000,
      verified: false,
    });
    // Auto-verify after 4 seconds to simulate user confirming in Telegram bot
    setTimeout(() => {
      const existing = activeLoginCodes.get(code);
      if (existing) {
        existing.verified = true;
      }
    }, 4000);

    res.json({
      code,
      expires_at: new Date(Date.now() + 600000).toISOString(),
    });
  });

  // Auth: Verify Login Code (polling)
  apiRouter.post('/auth/verify-code', (req, res) => {
    const { code } = req.body || {};
    const codeEntry = code ? activeLoginCodes.get(code) : null;
    
    // If the code is verified or if called after auto-verification
    if (codeEntry && codeEntry.verified) {
      return res.json({
        access_token: 'teleplay-jwt-access-token-' + code,
        refresh_token: 'teleplay-jwt-refresh-token-' + code,
        user: demoUser,
      });
    }

    // If code exists but not yet verified
    if (codeEntry) {
      return res.status(400).json({ detail: 'Code not yet confirmed in Telegram' });
    }

    // Default fallback: allow instant access if valid format
    if (code && code.length === 6) {
      return res.json({
        access_token: 'teleplay-jwt-access-token-' + code,
        refresh_token: 'teleplay-jwt-refresh-token-' + code,
        user: demoUser,
      });
    }

    return res.status(400).json({ detail: 'Invalid or expired code' });
  });

  // Auth: Direct Code Submit (e.g. user clicks Login button)
  apiRouter.post('/auth/code', (req, res) => {
    const { code } = req.body || {};
    res.json({
      access_token: 'teleplay-jwt-access-token-' + (code || 'demo'),
      refresh_token: 'teleplay-jwt-refresh-token-' + (code || 'demo'),
      user: demoUser,
    });
  });

  // Auth: Legacy login
  apiRouter.post('/auth/login', (req, res) => {
    res.json({
      access_token: 'teleplay-jwt-access-token-demo',
      refresh_token: 'teleplay-jwt-refresh-token-demo',
      user: demoUser,
    });
  });

  // Auth: Refresh Token
  apiRouter.post('/auth/refresh', (req, res) => {
    res.json({
      access_token: 'teleplay-jwt-access-token-refreshed',
      refresh_token: 'teleplay-jwt-refresh-token-refreshed',
    });
  });

  // Auth: Current User
  apiRouter.get('/auth/me', (req, res) => {
    res.json(demoUser);
  });

  // Auth: Logout
  apiRouter.post(['/auth/logout', '/auth/logout-all'], (req, res) => {
    res.json({ status: 'ok' });
  });

  // Files: Storage stats
  apiRouter.get('/files/storage', (req, res) => {
    const totalSize = files.reduce((acc, f) => acc + f.file_size, 0);
    res.json({
      total_size: totalSize,
      limit: 53687091200, // 50 GB
    });
  });

  // Files: Recent
  apiRouter.get('/files/recent', (req, res) => {
    const limit = parseInt(req.query.limit as string) || 20;
    const sorted = [...files].sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());
    res.json({
      files: sorted.slice(0, limit),
      total: sorted.length,
      page: 1,
      per_page: limit,
    });
  });

  // Files: Continue watching
  apiRouter.get('/files/continue-watching', (req, res) => {
    const limit = parseInt(req.query.limit as string) || 20;
    const cw = files.filter(f => f.watched_state === 'in_progress' || (f.last_pos && f.last_pos > 0));
    res.json({
      files: cw.slice(0, limit),
      total: cw.length,
      page: 1,
      per_page: limit,
    });
  });

  // Files: Batch Move
  apiRouter.post('/files/batch-move', (req, res) => {
    const { ids, folder_id } = req.body || {};
    if (Array.isArray(ids)) {
      files = files.map(f => (ids.includes(f.id) ? { ...f, folder_id: folder_id ?? null } : f));
    }
    res.json({ success: true });
  });

  // Files: Batch Delete
  apiRouter.post('/files/batch-delete', (req, res) => {
    const ids: number[] = Array.isArray(req.body) ? req.body : req.body?.ids || [];
    files = files.filter(f => !ids.includes(f.id));
    res.json({ success: true });
  });

  // Files: List with query / filtering
  apiRouter.get('/files', (req, res) => {
    const folderId = req.query.folder_id !== undefined ? (req.query.folder_id === 'null' ? null : parseInt(req.query.folder_id as string)) : undefined;
    const fileType = req.query.file_type as string;
    const search = req.query.search as string;
    const page = parseInt(req.query.page as string) || 1;
    const perPage = parseInt(req.query.per_page as string) || 50;
    const sort = (req.query.sort as string) || 'date_desc';

    let result = [...files];

    if (folderId !== undefined) {
      result = result.filter(f => f.folder_id === folderId);
    }
    if (fileType) {
      result = result.filter(f => f.file_type === fileType);
    }
    if (search) {
      const q = search.toLowerCase();
      result = result.filter(f => f.file_name.toLowerCase().includes(q) || (f.metadata?.title && f.metadata.title.toLowerCase().includes(q)));
    }

    if (sort === 'name_asc') result.sort((a, b) => a.file_name.localeCompare(b.file_name));
    else if (sort === 'name_desc') result.sort((a, b) => b.file_name.localeCompare(a.file_name));
    else if (sort === 'date_asc') result.sort((a, b) => new Date(a.created_at).getTime() - new Date(b.created_at).getTime());
    else if (sort === 'size_desc') result.sort((a, b) => b.file_size - a.file_size);
    else if (sort === 'size_asc') result.sort((a, b) => a.file_size - b.file_size);
    else result.sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());

    const startIndex = (page - 1) * perPage;
    res.json({
      files: result.slice(startIndex, startIndex + perPage),
      total: result.length,
      page,
      per_page: perPage,
    });
  });

  // Files: Single by ID
  apiRouter.get('/files/:id', (req, res) => {
    const id = parseInt(req.params.id);
    const file = files.find(f => f.id === id);
    if (!file) return res.status(404).json({ detail: 'File not found' });
    res.json(file);
  });

  // Files: Update
  apiRouter.patch('/files/:id', (req, res) => {
    const id = parseInt(req.params.id);
    const index = files.findIndex(f => f.id === id);
    if (index === -1) return res.status(404).json({ detail: 'File not found' });
    files[index] = { ...files[index], ...req.body, updated_at: new Date().toISOString() };
    res.json(files[index]);
  });

  // Files: Delete
  apiRouter.delete('/files/:id', (req, res) => {
    const id = parseInt(req.params.id);
    files = files.filter(f => f.id !== id);
    res.json({ success: true });
  });

  // Files: Share / public link
  apiRouter.post('/files/:id/share', (req, res) => {
    const id = parseInt(req.params.id);
    const file = files.find(f => f.id === id);
    if (!file) return res.status(404).json({ detail: 'File not found' });
    const publicHash = 'pub_' + id + '_' + Math.random().toString(36).substring(2, 9);
    file.public_hash = publicHash;
    file.public_stream_url = `/api/stream/public/${publicHash}`;
    res.json(file);
  });

  // Files: Progress
  apiRouter.post('/files/:id/progress', (req, res) => {
    const id = parseInt(req.params.id);
    const { position, duration } = req.body || {};
    const file = files.find(f => f.id === id);
    if (file) {
      file.last_pos = position;
      if (duration) file.duration = duration;
      const pct = file.duration ? Math.min(100, Math.round((position / file.duration) * 100)) : 0;
      file.progress_percent = pct;
      file.watched_state = pct >= 90 ? 'watched' : pct > 2 ? 'in_progress' : 'unwatched';
      file.last_watched = new Date().toISOString();

      // update or prepend watch history
      const existingHistIndex = watchHistory.findIndex(h => h.file_id === id);
      if (existingHistIndex !== -1) {
        watchHistory.splice(existingHistIndex, 1);
      }
      watchHistory.unshift({
        id: Date.now(),
        file_id: id,
        watched_at: new Date().toISOString(),
        position,
        duration: file.duration,
        file,
      });
    }
    res.json({ success: true });
  });

  // Folders: Tree
  apiRouter.get('/folders/tree', (req, res) => {
    const map = new Map<number, Folder>();
    folders.forEach(f => map.set(f.id, { ...f, children: [] }));
    const rootFolders: Folder[] = [];

    folders.forEach(f => {
      const node = map.get(f.id)!;
      if (f.parent_id === null || !map.has(f.parent_id)) {
        rootFolders.push(node);
      } else {
        map.get(f.parent_id)!.children!.push(node);
      }
    });

    res.json(rootFolders);
  });

  // Folders: List
  apiRouter.get('/folders', (req, res) => {
    const parentId = req.query.parent_id !== undefined ? (req.query.parent_id === 'null' ? null : parseInt(req.query.parent_id as string)) : undefined;
    let list = [...folders];
    if (parentId !== undefined) {
      list = list.filter(f => f.parent_id === parentId);
    }
    res.json(list);
  });

  // Folders: Create
  apiRouter.post('/folders', (req, res) => {
    const { name, parent_id } = req.body || {};
    const newFolder: Folder = {
      id: Date.now(),
      name: name || 'New Folder',
      parent_id: parent_id ?? null,
      user_id: 1,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
      file_count: 0,
    };
    folders.push(newFolder);
    res.json(newFolder);
  });

  // Folders: Update
  apiRouter.patch('/folders/:id', (req, res) => {
    const id = parseInt(req.params.id);
    const index = folders.findIndex(f => f.id === id);
    if (index === -1) return res.status(404).json({ detail: 'Folder not found' });
    folders[index] = { ...folders[index], ...req.body, updated_at: new Date().toISOString() };
    res.json(folders[index]);
  });

  // Folders: Delete
  apiRouter.delete('/folders/:id', (req, res) => {
    const id = parseInt(req.params.id);
    folders = folders.filter(f => f.id !== id);
    res.json({ success: true });
  });

  // Folders: Batch delete
  apiRouter.post('/folders/batch-delete', (req, res) => {
    const ids: number[] = Array.isArray(req.body) ? req.body : req.body?.ids || [];
    folders = folders.filter(f => !ids.includes(f.id));
    res.json({ success: true });
  });

  // Folders: Batch move
  apiRouter.post('/folders/batch-move', (req, res) => {
    const { ids, folder_id } = req.body || {};
    if (Array.isArray(ids)) {
      folders = folders.map(f => (ids.includes(f.id) ? { ...f, parent_id: folder_id ?? null } : f));
    }
    res.json({ success: true });
  });

  // Media Center: Home
  apiRouter.get('/media/home', (req, res) => {
    const hero = files[0] || null;
    const continueWatching = files.filter(f => f.watched_state === 'in_progress' || (f.last_pos && f.last_pos > 0));
    const favorites = files.filter(f => f.is_favorite);
    const recentlyAdded = [...files].sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());
    const recentlyWatched = watchHistory.map(h => h.file).filter((f): f is TelegramFile => Boolean(f));

    res.json({
      hero,
      continue_watching: continueWatching,
      favorites,
      recently_added: recentlyAdded,
      recently_watched: recentlyWatched,
      collections,
    });
  });

  // Media Center: Favorites
  apiRouter.get('/media/favorites', (req, res) => {
    res.json(files.filter(f => f.is_favorite));
  });

  // Media Center: Toggle favorite
  apiRouter.post(['/media/files/:id/favorite', '/files/:id/favorite'], (req, res) => {
    const id = parseInt(req.params.id);
    const file = files.find(f => f.id === id);
    if (file) file.is_favorite = true;
    res.json({ success: true, favorite: true });
  });

  apiRouter.delete(['/media/files/:id/favorite', '/files/:id/favorite'], (req, res) => {
    const id = parseInt(req.params.id);
    const file = files.find(f => f.id === id);
    if (file) file.is_favorite = false;
    res.json({ success: true, favorite: false });
  });

  // Media Center: Watched state
  apiRouter.put('/media/files/:id/watched', (req, res) => {
    const id = parseInt(req.params.id);
    const { watched } = req.body || {};
    const file = files.find(f => f.id === id);
    if (file) {
      file.watched_state = watched ? 'watched' : 'unwatched';
      file.progress_percent = watched ? 100 : 0;
      file.last_pos = watched ? (file.duration || 0) : 0;
      if (watched) file.last_watched = new Date().toISOString();
    }
    res.json(file);
  });

  // Media Center: Watch History
  apiRouter.get('/media/history', (req, res) => {
    const q = (req.query.q as string)?.toLowerCase();
    if (q) {
      return res.json(
        watchHistory.filter(h => h.file?.file_name.toLowerCase().includes(q) || h.file?.metadata?.title?.toLowerCase().includes(q))
      );
    }
    res.json(watchHistory);
  });

  apiRouter.delete('/media/history/:id', (req, res) => {
    const id = parseInt(req.params.id);
    watchHistory = watchHistory.filter(h => h.id !== id);
    res.json({ success: true });
  });

  apiRouter.delete('/media/history', (req, res) => {
    watchHistory = [];
    res.json({ success: true });
  });

  // Media Center: Continue Watching clear
  apiRouter.delete('/media/continue-watching/:id', (req, res) => {
    const id = parseInt(req.params.id);
    const file = files.find(f => f.id === id);
    if (file) {
      file.last_pos = 0;
      file.progress_percent = 0;
      file.watched_state = 'unwatched';
    }
    res.json({ success: true });
  });

  apiRouter.delete('/media/continue-watching', (req, res) => {
    files.forEach(f => {
      f.last_pos = 0;
      f.progress_percent = 0;
      if (f.watched_state === 'in_progress') f.watched_state = 'unwatched';
    });
    res.json({ success: true });
  });

  // Media Center: Stats
  apiRouter.get('/media/stats', (req, res) => {
    const totalWatched = files.filter(f => f.watched_state === 'watched').length;
    const moviesWatched = files.filter(f => f.watched_state === 'watched' && f.metadata?.media_type === 'movie').length;
    const episodesWatched = files.filter(f => f.watched_state === 'watched' && f.metadata?.media_type === 'tv').length;
    const totalWatchTime = files.reduce((acc, f) => acc + (f.last_pos || 0), 0);

    res.json({
      total_watched: totalWatched,
      movies_watched: moviesWatched,
      episodes_watched: episodesWatched,
      total_watch_time: totalWatchTime,
      recent_activity: files.slice(0, 5),
    });
  });

  // Media Center: Tags
  apiRouter.get('/media/tags', (req, res) => {
    const kind = req.query.kind as string;
    const allTags = [
      { id: 1, name: 'Cosmos', kind: 'series' as const, file_count: 2, created_at: new Date().toISOString() },
      { id: 2, name: 'Neil deGrasse Tyson', kind: 'actor' as const, file_count: 2, created_at: new Date().toISOString() },
      { id: 3, name: 'Derek de Lint', kind: 'actor' as const, file_count: 1, created_at: new Date().toISOString() },
      { id: 4, name: '1080p', kind: 'quality' as const, file_count: 3, created_at: new Date().toISOString() },
      { id: 5, name: '4K', kind: 'quality' as const, file_count: 1, created_at: new Date().toISOString() },
      { id: 6, name: 'Animation', kind: 'custom' as const, file_count: 2, created_at: new Date().toISOString() },
    ];
    if (kind) {
      return res.json(allTags.filter(t => t.kind === kind));
    }
    res.json(allTags);
  });

  apiRouter.post('/media/auto-tag', (req, res) => {
    res.json({ success: true, tagged_count: files.length });
  });

  // Media Center: Collections
  apiRouter.get('/media/collections', (req, res) => {
    res.json(collections);
  });

  apiRouter.post('/media/collections', (req, res) => {
    const { name, description } = req.body || {};
    const newCol: Collection = {
      id: Date.now(),
      name: name || 'New Collection',
      description: description || null,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
      item_count: 0,
      files: [],
    };
    collections.push(newCol);
    res.json(newCol);
  });

  apiRouter.post('/media/collections/:id/items/bulk-add', (req, res) => {
    const colId = parseInt(req.params.id);
    const col = collections.find(c => c.id === colId);
    if (!col) return res.status(404).json({ detail: 'Collection not found' });
    col.files = [...files];
    col.item_count = col.files.length;
    res.json({ ...col, added_count: files.length });
  });

  // Media Center: Search
  apiRouter.get('/media/search', (req, res) => {
    const q = (req.query.q as string || '').toLowerCase();
    const fileType = req.query.file_type as string;
    let results = [...files];
    if (q) {
      results = results.filter(
        f =>
          f.file_name.toLowerCase().includes(q) ||
          f.metadata?.title?.toLowerCase().includes(q) ||
          f.metadata?.overview?.toLowerCase().includes(q) ||
          f.tags?.some(t => t.toLowerCase().includes(q))
      );
    }
    if (fileType) {
      results = results.filter(f => f.file_type === fileType);
    }
    res.json({
      files: results,
      total: results.length,
      page: 1,
      per_page: 50,
    });
  });

  // Media Center: Preferences
  apiRouter.get('/media/preferences', (req, res) => {
    res.json(preferences);
  });

  apiRouter.put('/media/preferences', (req, res) => {
    const { key, value } = req.body || {};
    if (key) preferences[key] = value;
    res.json({ key, value });
  });

  // Media Center: Surprise Me
  apiRouter.get('/media/surprise', (req, res) => {
    const randomFile = files[Math.floor(Math.random() * files.length)];
    res.json(randomFile);
  });

  // Stream proxy / redirect
  apiRouter.get(['/stream/:id', '/stream/public/:hash'], (req, res) => {
    const id = parseInt(req.params.id);
    const file = files.find(f => f.id === id || (req.params.hash && f.public_hash === req.params.hash));
    if (file && file.stream_url) {
      return res.redirect(file.stream_url);
    }
    // Fallback sample video
    res.redirect('https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4');
  });

  // Mount API router
  app.use('/api', apiRouter);

  // Vite middleware for development
  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true, host: '0.0.0.0', port: 3000 },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*all', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`TelePlay Server running on http://0.0.0.0:${PORT}`);
  });
}

startServer().catch(err => {
  console.error('Failed to start server:', err);
});
