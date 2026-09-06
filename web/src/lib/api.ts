/**
 * API client and hooks for TelePlay backend.
 */
import axios from 'axios';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

// Types
export interface User {
    id: number;
    telegram_id: number;
    username: string | null;
    first_name: string | null;
    last_name: string | null;
    created_at: string;
    last_active: string;
}

export interface Folder {
    id: number;
    name: string;
    parent_id: number | null;
    user_id: number;
    created_at: string;
    updated_at: string;
    file_count: number;
    children?: Folder[];
}

export interface TelegramFile {
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

export interface MediaMetadata {
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

export interface MediaHome {
    hero: TelegramFile | null;
    continue_watching: TelegramFile[];
    favorites: TelegramFile[];
    recently_added: TelegramFile[];
    recently_watched: TelegramFile[];
    collections: Collection[];
}

export interface Collection {
    id: number;
    name: string;
    description?: string | null;
    created_at: string;
    updated_at: string;
    item_count: number;
    files?: TelegramFile[];
}

export interface HistoryEntry {
    id: number;
    file_id: number;
    watched_at: string;
    position?: number | null;
    duration?: number | null;
    file?: TelegramFile | null;
}

export interface MediaStats {
    total_watched: number;
    movies_watched: number;
    episodes_watched: number;
    total_watch_time: number;
    recent_activity: TelegramFile[];
}

export interface MediaTag {
    id: number;
    name: string;
    kind: 'series' | 'actor' | 'quality' | 'codec' | 'custom';
    value?: string | null;
    created_at: string;
    file_count: number;
}

export interface FileListResponse {
    files: TelegramFile[];
    total: number;
    page: number;
    per_page: number;
}

export interface BotInfo {
    username: string;
    name?: string;
    server_version: string;
}

export interface LoginCodeResponse {
    code: string;
    expires_at: string;
}

export interface StorageStats {
    total_size: number;
    limit: number;
}

export interface StorageStats {
    total_size: number;
    limit: number;
}

export interface StorageStats {
    total_size: number;
    limit: number;
}

export interface StorageStats {
    total_size: number;
    limit: number;
}

export interface AuthResponse {
    access_token: string;
    refresh_token: string;
    user: User;
}

// API client
export const api = axios.create({
    baseURL: '/api',
});

// Add auth token to requests
api.interceptors.request.use((config) => {
    const token = localStorage.getItem('access_token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// Queue for failed requests during token refresh
let isRefreshing = false;
let failedQueue: Array<{
    resolve: (token: string) => void;
    reject: (error: any) => void;
}> = [];

const processQueue = (error: any, token: string | null = null) => {
    failedQueue.forEach((prom) => {
        if (error) {
            prom.reject(error);
        } else {
            prom.resolve(token!);
        }
    });

    failedQueue = [];
};

// Handle 401 and 429 errors
api.interceptors.response.use(
    (response) => response,
    async (error) => {
        const originalRequest = error.config;

        if (error.response?.status === 401 && !originalRequest._retry) {
            if (originalRequest.url.includes('/auth/refresh')) {
                // Refresh token itself failed/expired - clear everything
                localStorage.removeItem('access_token');
                localStorage.removeItem('refresh_token');
                localStorage.removeItem('user');
                window.location.href = '/login';
                return Promise.reject(error);
            }

            if (isRefreshing) {
                return new Promise(function (resolve, reject) {
                    failedQueue.push({ resolve, reject });
                })
                    .then((token) => {
                        originalRequest.headers['Authorization'] = 'Bearer ' + token;
                        return api(originalRequest);
                    })
                    .catch((err) => {
                        return Promise.reject(err);
                    });
            }

            originalRequest._retry = true;
            isRefreshing = true;

            try {
                const refreshToken = localStorage.getItem('refresh_token');
                if (!refreshToken) {
                    throw new Error('No refresh token available');
                }

                const { data } = await axios.post('/api/auth/refresh', {
                    refresh_token: refreshToken,
                });

                const { access_token, refresh_token } = data;

                localStorage.setItem('access_token', access_token);
                localStorage.setItem('refresh_token', refresh_token);

                api.defaults.headers.common['Authorization'] = 'Bearer ' + access_token;
                originalRequest.headers['Authorization'] = 'Bearer ' + access_token;

                processQueue(null, access_token);
                return api(originalRequest);
            } catch (err) {
                processQueue(err, null);
                localStorage.removeItem('access_token');
                localStorage.removeItem('refresh_token');
                localStorage.removeItem('user');
                window.location.href = '/login';
                return Promise.reject(err);
            } finally {
                isRefreshing = false;
            }
        } else if (error.response?.status === 429) {
            error.message = 'Too many requests. Please wait a moment and try again.';
        }
        
        return Promise.reject(error);
    }
);

// ============== Auth Hooks ==============

export const useCurrentUser = () => {
    return useQuery({
        queryKey: ['currentUser'],
        queryFn: async () => {
            const { data } = await api.get<User>('/auth/me');
            return data;
        },
        retry: false,
    });
};

export const useLoginWithCode = () => {
    return useMutation({
        mutationFn: async (code: string) => {
            const { data } = await api.post<{ access_token: string; refresh_token: string }>('/auth/code', { code });
            return data;
        },
    });
};

export const useLogoutAll = () => {
    return useMutation({
        mutationFn: async () => {
            await api.post('/auth/logout-all');
        },
    });
};

export const useBotInfo = () => {
    return useQuery({
        queryKey: ['botInfo'],
        queryFn: async () => {
            const { data } = await api.get<BotInfo>('/auth/bot/info');
            return data;
        },
        staleTime: Infinity, // Bot info doesn't change during session
    });
};

export const useGenerateLoginCode = () => {
    return useMutation({
        mutationFn: async () => {
            const { data } = await api.post<LoginCodeResponse>('/auth/generate-code');
            return data;
        },
    });
};

export const useVerifyLoginCode = () => {
    return useMutation({
        mutationFn: async (code: string) => {
            const { data } = await api.post<AuthResponse>('/auth/verify-code', { code });
            return data;
        },
    });
};

// ============== Files Hooks ==============

export const useFiles = (folderId?: number | null, fileType?: string, search?: string, page = 1) => {
    return useQuery({
        queryKey: ['files', folderId, fileType, search, page],
        queryFn: async () => {
            const params: Record<string, any> = {};
            if (folderId !== undefined) params.folder_id = folderId;
            if (fileType) params.file_type = fileType;
            if (search) params.search = search;
            params.page = page;
            params.per_page = 50; // Load 50 files per page
            const { data } = await api.get<FileListResponse>('/files', { params });
            return data;
        },
        staleTime: 30000, // Keep data fresh for 30s to avoid over-fetching
    });
};

export const useFile = (fileId: number) => {
    return useQuery({
        queryKey: ['file', fileId],
        queryFn: async () => {
            const { data } = await api.get<TelegramFile>(`/files/${fileId}`);
            return data;
        },
        enabled: !!fileId,
    });
};

export const useUpdateFile = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async ({ id, ...data }: { id: number; file_name?: string; folder_id?: number | null }) => {
            const { data: result } = await api.patch<TelegramFile>(`/files/${id}`, data);
            return result;
        },
        onSuccess: () => {
            // Invalidate both files and folders to ensure UI updates for moves
            queryClient.invalidateQueries({ queryKey: ['files'] });
            queryClient.invalidateQueries({ queryKey: ['folders'] });
            queryClient.invalidateQueries({ queryKey: ['folderTree'] });
        },
    });
};

export const useDeleteFile = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (id: number) => {
            await api.delete(`/files/${id}`);
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['files'] });
        },
    });
};

export const useDeleteFiles = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (ids: number[]) => {
            await api.post('/files/batch-delete', ids as any); // Axios automatically handles array as JSON body
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['files'] });
             queryClient.invalidateQueries({ queryKey: ['folders'] }); // Files might be inside folders affecting counts
             queryClient.invalidateQueries({ queryKey: ['storage'] });
        },
    });
};

export const useMoveFiles = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async ({ ids, folderId }: { ids: number[]; folderId: number | null }) => {
            await api.post('/files/batch-move', { ids, folder_id: folderId });
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['files'] });
            queryClient.invalidateQueries({ queryKey: ['folders'] });
            queryClient.invalidateQueries({ queryKey: ['folderTree'] });
        },
    });
};

export const useRecentFiles = (limit = 20) => {
    return useQuery<FileListResponse>({
        queryKey: ['files', 'recent', limit],
        queryFn: async () => {
             const { data } = await api.get<FileListResponse>('/files/recent', { params: { limit } });
             return data;
        },
    });
};

export const useContinueWatching = (limit = 20) => {
    return useQuery<FileListResponse>({
        queryKey: ['files', 'continue-watching', limit],
        queryFn: async () => {
             const { data } = await api.get<FileListResponse>('/files/continue-watching', { params: { limit } });
             return data;
        },
    });
};

// ============== Media Center Hooks ==============

export const useMediaHome = (limit = 20) => useQuery<MediaHome>({
    queryKey: ['media-home', limit],
    queryFn: async () => (await api.get<MediaHome>('/media/home', { params: { limit } })).data,
    staleTime: 30000,
});

export const useFavorites = () => useQuery<TelegramFile[]>({
    queryKey: ['favorites'],
    queryFn: async () => (await api.get<TelegramFile[]>('/media/favorites')).data,
    staleTime: 30000,
});

export const useHistory = (query = '') => useQuery<HistoryEntry[]>({
    queryKey: ['history', query],
    queryFn: async () => (await api.get<HistoryEntry[]>('/media/history', { params: { q: query || undefined } })).data,
    staleTime: 30000,
});

export const useMediaSearch = (query: string, filters: Record<string, string | number | boolean | undefined> = {}) => useQuery<FileListResponse>({
    queryKey: ['media-search', query, filters],
    queryFn: async () => (await api.get<FileListResponse>('/media/search', { params: { q: query, ...filters } })).data,
    enabled: query.trim().length > 0 || Object.values(filters).some((value) => value !== undefined),
    staleTime: 15000,
});

export const useMediaTags = (kind?: MediaTag['kind']) => useQuery<MediaTag[]>({
    queryKey: ['media-tags', kind],
    queryFn: async () => (await api.get<MediaTag[]>('/media/tags', { params: { kind } })).data,
    staleTime: 60000,
});

export const useAutoTagLibrary = () => {
    const queryClient = useQueryClient();
    return useMutation<unknown, Error, number | undefined>({
        mutationFn: async (limit = 5000) => (await api.post('/media/auto-tag', null, { params: { limit } })).data,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['media-tags'] });
            queryClient.invalidateQueries({ queryKey: ['media-home'] });
            queryClient.invalidateQueries({ queryKey: ['files'] });
        },
    });
};

export const useToggleFavorite = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async ({ fileId, favorite }: { fileId: number; favorite: boolean }) => {
            if (favorite) await api.post(`/media/files/${fileId}/favorite`);
            else await api.delete(`/media/files/${fileId}/favorite`);
            return favorite;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['favorites'] });
            queryClient.invalidateQueries({ queryKey: ['media-home'] });
            queryClient.invalidateQueries({ queryKey: ['files'] });
            queryClient.invalidateQueries({ queryKey: ['file'] });
        },
    });
};

export const useSetWatched = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async ({ fileId, watched }: { fileId: number; watched: boolean }) =>
            (await api.put<TelegramFile>(`/media/files/${fileId}/watched`, { watched })).data,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['media-home'] });
            queryClient.invalidateQueries({ queryKey: ['favorites'] });
            queryClient.invalidateQueries({ queryKey: ['history'] });
            queryClient.invalidateQueries({ queryKey: ['files'] });
            queryClient.invalidateQueries({ queryKey: ['file'] });
        },
    });
};

export const useCollections = () => useQuery<Collection[]>({
    queryKey: ['collections'],
    queryFn: async () => (await api.get<Collection[]>('/media/collections')).data,
    staleTime: 30000,
});

export const useCreateCollection = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (payload: { name: string; description?: string }) =>
            (await api.post<Collection>('/media/collections', payload)).data,
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ['collections'] }),
    });
};

export const useDeleteHistory = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (entryId: number) => api.delete(`/media/history/${entryId}`),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ['history'] }),
    });
};

export const useClearHistory = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async () => api.delete('/media/history'),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ['history'] }),
    });
};

export const useMediaStats = () => useQuery<MediaStats>({
    queryKey: ['media-stats'],
    queryFn: async () => (await api.get<MediaStats>('/media/stats')).data,
    staleTime: 60000,
});

export const useSurpriseMe = () => useMutation({
    mutationFn: async (params: { file_type?: string; unwatched?: boolean; favorite?: boolean } = {}) =>
        (await api.get<TelegramFile>('/media/surprise', { params })).data,
});

export const useStorageStats = () => {
    return useQuery<StorageStats>({
        queryKey: ['storage'],
        queryFn: async () => {
             const { data } = await api.get<StorageStats>('/files/storage');
             return data;
        },
        staleTime: 60000,
    });
};

export const useUpdateProgress = () => {
    return useMutation({
        mutationFn: async ({ fileId, position, duration }: { fileId: number; position: number; duration?: number }) => {
            await api.post(`/files/${fileId}/progress`, { position, duration });
        },
    });
};

// ============== Folders Hooks ==============

export const useMoveFolders = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async ({ ids, folderId }: { ids: number[]; folderId: number | null }) => {
            await api.post('/folders/batch-move', { ids, folder_id: folderId });
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['folders'] });
            queryClient.invalidateQueries({ queryKey: ['folderTree'] });
        },
    });
};

export const useFolders = (parentId?: number | null) => {
    return useQuery({
        queryKey: ['folders', parentId],
        queryFn: async () => {
            const params: Record<string, any> = {};
            if (parentId !== undefined) params.parent_id = parentId;
            const { data } = await api.get<Folder[]>('/folders', { params });
            return data;
        },
        staleTime: 60000, // Folders change less often
    });
};

export const useFolderTree = () => {
    return useQuery({
        queryKey: ['folderTree'],
        queryFn: async () => {
            const { data } = await api.get<Folder[]>('/folders/tree');
            return data;
        },
        staleTime: 60000,
    });
};

export const useCreateFolder = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (data: { name: string; parent_id?: number | null }) => {
            const { data: result } = await api.post<Folder>('/folders', data);
            return result;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['folders'] });
            queryClient.invalidateQueries({ queryKey: ['folderTree'] });
        },
    });
};

export const useUpdateFolder = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async ({ id, ...data }: { id: number; name?: string; parent_id?: number | null }) => {
            const { data: result } = await api.patch<Folder>(`/folders/${id}`, data);
            return result;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['folders'] });
            queryClient.invalidateQueries({ queryKey: ['folderTree'] });
        },
    });
};

export const useDeleteFolder = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async ({ id, moveFilesTo }: { id: number; moveFilesTo?: number | null }) => {
            const params = moveFilesTo !== undefined ? { move_files_to: moveFilesTo } : {};
            await api.delete(`/folders/${id}`, { params });
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['folders'] });
            queryClient.invalidateQueries({ queryKey: ['folderTree'] });
            queryClient.invalidateQueries({ queryKey: ['files'] });
        },
    });
};

export const useDeleteFolders = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (ids: number[]) => {
            await api.post('/folders/batch-delete', ids as any);
        },
        onSuccess: () => {
             queryClient.invalidateQueries({ queryKey: ['folders'] });
             queryClient.invalidateQueries({ queryKey: ['folderTree'] });
             queryClient.invalidateQueries({ queryKey: ['files'] });
             queryClient.invalidateQueries({ queryKey: ['storage'] });
        },
    });
};

// ============== Utilities ==============

export const formatFileSize = (bytes: number): string => {
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let size = bytes;
    let unitIndex = 0;
    while (size >= 1024 && unitIndex < units.length - 1) {
        size /= 1024;
        unitIndex++;
    }
    return `${size.toFixed(1)} ${units[unitIndex]}`;
};

export const formatDuration = (seconds: number | null): string => {
    if (!seconds) return '';
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = Math.floor(seconds % 60);
    if (hours > 0) {
        return `${hours}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    }
    return `${minutes}:${secs.toString().padStart(2, '0')}`;
};

export const getFileIcon = (fileType: string): string => {
    switch (fileType) {
        case 'video': return '🎬';
        case 'audio': return '🎵';
        case 'image': return '🖼️';
        case 'document': return '📄';
        default: return '📎';
    }
};
