import { ArrowRight, Check, Compass, Film, Heart, Menu, Play, Shuffle, Sparkles, Star } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMediaHome, useSetWatched, useSurpriseMe, useToggleFavorite, TelegramFile } from '../lib/api';
import { useAppStore } from '../lib/store';
import Sidebar from './Sidebar';
import MobileTabBar from './MobileTabBar';
import MediaCard from './MediaCard';
import Toasts from './Toasts';

function artwork(file: TelegramFile | null) {
    const source = file?.metadata?.backdrop_url || file?.metadata?.poster_url || file?.thumbnail_url;
    if (!source) return undefined;
    if (source.startsWith('http')) return source;
    return `${window.location.origin}${source}`;
}

export default function MediaCenterPage() {
    const { data, isLoading, isError, refetch } = useMediaHome();
    const [sidebarOpen, setSidebarOpen] = useState(() => window.innerWidth >= 768);
    const [search, setSearch] = useState('');
    const navigate = useNavigate();
    const setPreviewFile = useAppStore((state) => state.setPreviewFile);
    const favoriteMutation = useToggleFavorite();
    const watchedMutation = useSetWatched();
    const surpriseMutation = useSurpriseMe();
    const hero = data?.hero || null;
    const heroArt = artwork(hero);

    const play = (file: TelegramFile | null) => {
        if (file && (file.file_type === 'video' || file.file_type === 'audio')) setPreviewFile(file);
    };
    const runSearch = (event: React.FormEvent) => {
        event.preventDefault();
        if (search.trim()) navigate(`/search?q=${encodeURIComponent(search.trim())}`);
    };
    const surprise = async () => {
        const result = await surpriseMutation.mutateAsync({ unwatched: true });
        play(result);
    };

    return (
        <div className="min-h-screen bg-dark-950 text-white">
            <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />
            <main className={`min-h-screen transition-[margin] duration-300 ${sidebarOpen ? 'md:ml-64' : ''}`}>
                <header className="sticky top-0 z-30 flex h-16 items-center gap-4 border-b border-white/[0.06] bg-dark-950/80 px-4 backdrop-blur-xl sm:px-8">
                    <button onClick={() => setSidebarOpen((value) => !value)} className="rounded-xl p-2 text-dark-400 hover:bg-white/5 hover:text-white"><Menu className="h-5 w-5" /></button>
                    <form onSubmit={runSearch} className="relative max-w-xl flex-1">
                        <Compass className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-dark-600" />
                        <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search your library…" className="input-search w-full pl-10" />
                    </form>
                    <button onClick={surprise} disabled={surpriseMutation.isPending} className="hidden items-center gap-2 rounded-xl border border-primary-400/20 bg-primary-500/10 px-3 py-2 text-xs font-semibold text-primary-200 transition hover:bg-primary-500/20 sm:flex"><Shuffle className="h-4 w-4" /> Surprise me</button>
                </header>

                {isLoading ? <DashboardSkeleton /> : isError ? <ErrorState onRetry={() => refetch()} /> : (
                    <div className="pb-24 md:pb-16">
                        {hero ? <section className="relative isolate min-h-[420px] overflow-hidden sm:min-h-[500px]">
                            {heroArt ? <img src={heroArt} alt="" className="absolute inset-0 -z-20 h-full w-full object-cover opacity-65" /> : <div className="absolute inset-0 -z-20 bg-[radial-gradient(circle_at_20%_20%,#6d28d9,#111827_50%,#020617)]" />}
                            <div className="absolute inset-0 -z-10 bg-[linear-gradient(90deg,#020617_0%,rgba(2,6,23,.82)_35%,rgba(2,6,23,.2)_75%),linear-gradient(0deg,#020617_0%,transparent_65%)]" />
                            <div className="flex min-h-[420px] max-w-3xl flex-col justify-end px-6 pb-12 sm:min-h-[500px] sm:px-12 sm:pb-16 lg:px-16">
                                <div className="mb-4 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.22em] text-primary-200"><Sparkles className="h-4 w-4" /> Featured from your library</div>
                                <h1 className="max-w-2xl text-4xl font-bold tracking-tight text-white sm:text-6xl">{hero.metadata?.title || hero.file_name}</h1>
                                <div className="mt-4 flex flex-wrap items-center gap-3 text-sm text-dark-300"><span>{hero.metadata?.year || 'Recently added'}</span>{hero.duration ? <><span className="h-1 w-1 rounded-full bg-dark-500" /><span>{Math.floor(hero.duration / 60)} min</span></> : null}{hero.metadata?.rating ? <><span className="h-1 w-1 rounded-full bg-dark-500" /><span className="inline-flex items-center gap-1"><Star className="h-3.5 w-3.5 fill-amber-300 text-amber-300" /> {hero.metadata.rating.toFixed(1)}</span></> : null}</div>
                                {hero.metadata?.overview && <p className="mt-4 line-clamp-3 max-w-xl text-sm leading-6 text-dark-300 sm:text-base">{hero.metadata.overview}</p>}
                                <div className="mt-7 flex flex-wrap gap-3">
                                    <button onClick={() => play(hero)} className="btn-primary inline-flex items-center gap-2 px-5 py-3"><Play className="h-4 w-4 fill-current" /> {hero.watched_state === 'in_progress' ? 'Resume' : 'Play now'}</button>
                                    <button onClick={() => favoriteMutation.mutate({ fileId: hero.id, favorite: !hero.is_favorite })} className="btn-secondary inline-flex items-center gap-2 px-5 py-3"><Heart className="h-4 w-4" fill={hero.is_favorite ? 'currentColor' : 'none'} /> {hero.is_favorite ? 'Favorited' : 'Favorite'}</button>
                                    <button onClick={() => watchedMutation.mutate({ fileId: hero.id, watched: hero.watched_state !== 'watched' })} className="btn-ghost inline-flex items-center gap-2 px-4 py-3">{hero.watched_state === 'watched' ? <><Check className="h-4 w-4" /> Watched</> : 'Mark watched'}</button>
                                </div>
                            </div>
                        </section> : <EmptyHome onBrowse={() => navigate('/library')} />}

                        <div className="space-y-10 px-6 pt-8 sm:px-10 lg:px-16">
                            <Shelf title="Continue watching" eyebrow="Pick up where you left off" files={data?.continue_watching || []} onSeeAll={() => navigate('/library')} />
                            <Shelf title="Favorites" eyebrow="Your personal shortlist" files={data?.favorites || []} onSeeAll={() => navigate('/favorites')} />
                            <Shelf title="Recently added" eyebrow="Fresh in your archive" files={data?.recently_added || []} onSeeAll={() => navigate('/library')} />
                            <Shelf title="Recently watched" eyebrow="Your viewing trail" files={data?.recently_watched || []} onSeeAll={() => navigate('/history')} />
                            {(data?.collections || []).length > 0 && <section><SectionHeading eyebrow="Curated by you" title="Collections" onSeeAll={() => navigate('/collections')} /><div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">{data?.collections.map((collection) => <button key={collection.id} onClick={() => navigate('/collections')} className="group rounded-2xl border border-white/[0.07] bg-white/[0.03] p-5 text-left transition hover:-translate-y-0.5 hover:border-primary-400/30 hover:bg-primary-500/10"><div className="mb-7 flex h-10 w-10 items-center justify-center rounded-xl bg-primary-500/15 text-primary-200"><Film className="h-5 w-5" /></div><p className="font-semibold text-white">{collection.name}</p><p className="mt-1 text-xs text-dark-500">{collection.item_count} items <ArrowRight className="ml-1 inline h-3 w-3 transition group-hover:translate-x-1" /></p></button>)}</div></section>}
                        </div>
                    </div>
                )}
            </main>
            <MobileTabBar />
            <Toasts />
        </div>
    );
}

function SectionHeading({ eyebrow, title, onSeeAll }: { eyebrow: string; title: string; onSeeAll: () => void }) { return <div className="flex items-end justify-between gap-4"><div><p className="text-[10px] font-semibold uppercase tracking-[0.22em] text-primary-300/80">{eyebrow}</p><h2 className="mt-1 text-2xl font-semibold tracking-tight text-white">{title}</h2></div><button onClick={onSeeAll} className="inline-flex items-center gap-1 text-xs font-medium text-dark-400 hover:text-white">See all <ArrowRight className="h-3.5 w-3.5" /></button></div> }
function Shelf({ title, eyebrow, files, onSeeAll }: { title: string; eyebrow: string; files: TelegramFile[]; onSeeAll: () => void }) { if (!files.length) return null; return <section><SectionHeading eyebrow={eyebrow} title={title} onSeeAll={onSeeAll} /><div className="no-scrollbar mt-4 flex gap-4 overflow-x-auto pb-2">{files.map((file) => <MediaCard key={file.id} file={file} />)}</div></section>; }
function DashboardSkeleton() { return <div className="animate-pulse p-6 sm:p-12"><div className="h-[420px] rounded-3xl bg-dark-800/60" /><div className="mt-10 h-8 w-48 rounded bg-dark-800" /><div className="mt-5 flex gap-4">{[1, 2, 3, 4, 5].map((item) => <div key={item} className="h-72 w-48 rounded-2xl bg-dark-800/60" />)}</div></div>; }
function ErrorState({ onRetry }: { onRetry: () => void }) { return <div className="flex min-h-[60vh] flex-col items-center justify-center p-6 text-center"><p className="text-lg font-semibold text-white">Your dashboard could not load</p><p className="mt-2 text-sm text-dark-400">Check the connection to your private library and try again.</p><button onClick={onRetry} className="btn-primary mt-6">Try again</button></div>; }
function EmptyHome({ onBrowse }: { onBrowse: () => void }) { return <div className="flex min-h-[60vh] flex-col items-center justify-center p-6 text-center"><div className="mb-5 rounded-2xl bg-primary-500/10 p-4 text-primary-300"><Film className="h-8 w-8" /></div><h1 className="text-3xl font-bold">Your cinema starts here</h1><p className="mt-3 max-w-md text-sm leading-6 text-dark-400">Send media to your TelePlay bot, then return here to build a private, beautifully organized library.</p><button onClick={onBrowse} className="btn-primary mt-7">Browse library</button></div>; }
