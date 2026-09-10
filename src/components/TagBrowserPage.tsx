import { useMemo, useState } from 'react';
import { ArrowLeft, Clapperboard, Menu, Search, UserRound } from 'lucide-react';
import { useAutoTagLibrary, useMediaSearch, useMediaTags, MediaTag } from '../lib/api';
import Sidebar from './Sidebar';
import MobileTabBar from './MobileTabBar';
import MediaCard from './MediaCard';
import Toasts from './Toasts';

export default function TagBrowserPage({ kind }: { kind: 'series' | 'actor' }) {
    const [sidebarOpen, setSidebarOpen] = useState(() => window.innerWidth >= 768);
    const [selected, setSelected] = useState<MediaTag | null>(null);
    const [tagFilter, setTagFilter] = useState('');
    const { data: tags, isLoading: tagsLoading } = useMediaTags(kind);
    const { data: results, isLoading: filesLoading } = useMediaSearch('', selected ? { tag: selected.value || selected.name } : {});
    const autoTag = useAutoTagLibrary();
    const isSeries = kind === 'series';
    const Icon = isSeries ? Clapperboard : UserRound;
    const filteredTags = useMemo(() => {
        const query = tagFilter.trim().toLocaleLowerCase();
        if (!query) return tags || [];
        return (tags || []).filter((tag) => tag.name.toLocaleLowerCase().includes(query));
    }, [tagFilter, tags]);
    const label = isSeries ? 'series' : 'actors';

    const clearSelection = () => {
        setSelected(null);
        setTagFilter('');
    };

    return (
        <div className="min-h-screen bg-dark-950 text-white">
            <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />
            <main className={`min-h-screen transition-[margin] duration-300 ${sidebarOpen ? 'md:ml-64' : ''}`}>
                <header className="flex h-16 items-center gap-4 border-b border-white/[0.06] px-4 sm:px-8">
                    <button onClick={() => setSidebarOpen((value) => !value)} className="rounded-xl p-2 text-dark-400 hover:bg-white/5 hover:text-white"><Menu className="h-5 w-5" /></button>
                    <div className="flex items-center gap-3"><div className="rounded-xl bg-primary-500/15 p-2 text-primary-200"><Icon className="h-5 w-5" /></div><div><p className="text-[10px] font-semibold uppercase tracking-[0.22em] text-primary-300/80">Auto-organized</p><h1 className="text-lg font-semibold">{isSeries ? 'Series' : 'Actors'}</h1></div></div>
                </header>
                <div className="p-6 pb-24 sm:p-10 md:pb-10 lg:p-16">
                    <div className="max-w-2xl"><p className="text-sm text-primary-200">{isSeries ? 'Find every episode by series, even when files live in different folders.' : 'Jump to every movie and series connected to an actor.'}</p><h2 className="mt-2 text-3xl font-bold">{isSeries ? 'Browse by series' : 'Browse by actor'}</h2><p className="mt-3 text-sm leading-6 text-dark-400">TelePlay reads patterns such as <code className="rounded bg-white/10 px-1.5 py-0.5 text-xs text-primary-200">S01E02</code>, <code className="rounded bg-white/10 px-1.5 py-0.5 text-xs text-primary-200">oyuncuA</code>, quality, and codec markers from filenames and keeps the original file untouched.</p></div>
                    <div className="mt-8 flex flex-wrap items-center justify-between gap-3"><div className="flex items-center gap-2 text-sm text-dark-400"><Search className="h-4 w-4" /> {tags?.length || 0} {label} detected</div><button onClick={() => autoTag.mutate(5000)} disabled={autoTag.isPending} className="btn-secondary text-xs">{autoTag.isPending ? 'Tagging library…' : 'Tag library now'}</button></div>

                    {selected ? (
                        <section className="mt-8 border-t border-white/[0.07] pt-6">
                            <div className="flex flex-wrap items-center justify-between gap-3">
                                <div>
                                    <button onClick={clearSelection} className="mb-3 inline-flex items-center gap-1 text-xs text-dark-500 hover:text-white"><ArrowLeft className="h-3 w-3" /> Back to all {label}</button>
                                    <h3 className="text-2xl font-semibold">{selected.name}</h3>
                                </div>
                                <p className="text-sm text-dark-500">{results?.total || 0} matching files</p>
                            </div>
                            {filesLoading ? <p className="mt-6 text-dark-400">Loading matching media…</p> : results?.files.length ? <div className="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-6">{results.files.map((file) => <MediaCard key={file.id} file={file} compact />)}</div> : <p className="mt-6 text-dark-500">No files found for this tag.</p>}
                        </section>
                    ) : (
                        <>
                            <div className="mt-5 max-w-md">
                                <label className="sr-only" htmlFor="tag-filter">Filter {label}</label>
                                <div className="relative"><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-dark-500" /><input id="tag-filter" value={tagFilter} onChange={(event) => setTagFilter(event.target.value)} placeholder={`Filter ${label}…`} className="w-full rounded-xl border border-white/[0.08] bg-white/[0.04] py-2.5 pl-9 pr-3 text-sm text-white outline-none placeholder:text-dark-500 focus:border-primary-400/50" /></div>
                            </div>
                            {tagsLoading ? <p className="mt-8 text-dark-400">Reading your filename tags…</p> : filteredTags.length ? <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">{filteredTags.map((tag) => <button key={tag.id} onClick={() => setSelected(tag)} className="rounded-2xl border border-white/[0.07] bg-white/[0.03] p-4 text-left transition hover:border-primary-400/30 hover:bg-white/[0.06]"><div className="flex items-center justify-between gap-3"><span className="truncate font-semibold text-white">{tag.name}</span><span className="rounded-full bg-white/10 px-2 py-0.5 text-[10px] text-dark-300">{tag.file_count}</span></div><p className="mt-2 text-[11px] uppercase tracking-[0.16em] text-dark-600">{isSeries ? 'Series' : 'Actor'}</p></button>)}</div> : <div className="mt-8 rounded-2xl border border-dashed border-white/10 p-10 text-center text-dark-500">{tagFilter ? `No ${label} match “${tagFilter}”.` : `No automatic ${label} tags yet. Upload a file with a recognizable filename and run “Tag library” from the library tools.`}</div>}
                        </>
                    )}
                </div>
            </main>
            <MobileTabBar />
            <Toasts />
        </div>
    );
}
