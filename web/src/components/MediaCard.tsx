import { Check, Heart, Play, RotateCcw, Star } from 'lucide-react';
import { TelegramFile, formatDuration, useSetWatched, useToggleFavorite } from '../lib/api';
import { useAppStore } from '../lib/store';

interface MediaCardProps {
    file: TelegramFile;
    compact?: boolean;
}

function artworkFor(file: TelegramFile) {
    const source = file.metadata?.poster_url || file.thumbnail_url;
    if (!source) return null;
    if (source.startsWith('http')) return source;
    return `${window.location.origin}${source}`;
}

export default function MediaCard({ file, compact = false }: MediaCardProps) {
    const setPreviewFile = useAppStore((state) => state.setPreviewFile);
    const favoriteMutation = useToggleFavorite();
    const watchedMutation = useSetWatched();
    const artwork = artworkFor(file);
    const title = file.metadata?.title || file.file_name;
    const progress = file.progress_percent || 0;
    const isPlayable = file.file_type === 'video' || file.file_type === 'audio';

    return (
        <article className={`group relative shrink-0 ${compact ? 'w-36 sm:w-44' : 'w-44 sm:w-52 md:w-56'}`}>
            <button
                type="button"
                className="w-full text-left focus-ring"
                onClick={() => isPlayable && setPreviewFile(file)}
                aria-label={`Play ${title}`}
            >
                <div className="relative aspect-[2/3] overflow-hidden rounded-2xl border border-white/[0.08] bg-dark-800 shadow-lg shadow-black/20 transition duration-300 group-hover:-translate-y-1 group-hover:border-primary-400/40 group-hover:shadow-primary-950/40">
                    {artwork ? (
                        <img src={artwork} alt="" className="h-full w-full object-cover transition duration-500 group-hover:scale-105" loading="lazy" />
                    ) : (
                        <div className="flex h-full items-end bg-[radial-gradient(circle_at_top,#5b21b6,#111827_58%,#020617)] p-4">
                            <span className="line-clamp-3 text-left text-lg font-semibold text-white">{title}</span>
                        </div>
                    )}
                    <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/10 to-transparent" />
                    <div className="absolute inset-x-0 bottom-0 p-3">
                        <p className="line-clamp-2 text-sm font-semibold text-white">{title}</p>
                        <div className="mt-1 flex items-center gap-2 text-[11px] text-dark-300">
                            {file.metadata?.year && <span>{file.metadata.year}</span>}
                            {file.duration && <span>{formatDuration(file.duration)}</span>}
                            {file.metadata?.rating ? <span className="inline-flex items-center gap-0.5"><Star className="h-3 w-3 fill-amber-300 text-amber-300" />{file.metadata.rating.toFixed(1)}</span> : null}
                        </div>
                    </div>
                    {progress > 0 && file.watched_state !== 'watched' && (
                        <div className="absolute inset-x-0 bottom-0 h-1 bg-white/20"><div className="h-full bg-primary-400" style={{ width: `${Math.min(100, progress)}%` }} /></div>
                    )}
                    {file.watched_state === 'watched' && <span className="absolute left-2 top-2 inline-flex items-center gap-1 rounded-full bg-emerald-400/90 px-2 py-1 text-[10px] font-bold text-emerald-950"><Check className="h-3 w-3" /> Watched</span>}
                    {file.watched_state === 'in_progress' && <span className="absolute left-2 top-2 rounded-full bg-primary-500/90 px-2 py-1 text-[10px] font-bold text-white">{Math.round(progress)}%</span>}
                </div>
            </button>
            <div className="pointer-events-none absolute right-2 top-2 flex gap-1 opacity-0 transition group-hover:pointer-events-auto group-hover:opacity-100">
                <button
                    type="button"
                    title={file.is_favorite ? 'Remove from favorites' : 'Add to favorites'}
                    className={`rounded-full border border-white/10 p-2 backdrop-blur-md transition ${file.is_favorite ? 'bg-pink-500/80 text-white' : 'bg-black/45 text-white hover:bg-pink-500/80'}`}
                    onClick={() => favoriteMutation.mutate({ fileId: file.id, favorite: !file.is_favorite })}
                >
                    <Heart className="h-3.5 w-3.5" fill={file.is_favorite ? 'currentColor' : 'none'} />
                </button>
                <button
                    type="button"
                    title={file.watched_state === 'watched' ? 'Mark unwatched' : 'Mark watched'}
                    className="rounded-full border border-white/10 bg-black/45 p-2 text-white backdrop-blur-md transition hover:bg-emerald-500/80"
                    onClick={() => watchedMutation.mutate({ fileId: file.id, watched: file.watched_state !== 'watched' })}
                >
                    {file.watched_state === 'watched' ? <RotateCcw className="h-3.5 w-3.5" /> : <Check className="h-3.5 w-3.5" />}
                </button>
            </div>
            <button type="button" className="mt-2 inline-flex items-center gap-1 text-xs font-medium text-dark-400 transition hover:text-white" onClick={() => isPlayable && setPreviewFile(file)}>
                {file.watched_state === 'in_progress' ? <><RotateCcw className="h-3 w-3" /> Resume</> : <><Play className="h-3 w-3 fill-current" /> Play</>}
            </button>
        </article>
    );
}
