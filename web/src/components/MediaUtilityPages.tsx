import { useState } from 'react';
import { BarChart3, CalendarDays, Clock3, FolderPlus, Menu, Search, Trash2 } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { useBulkAddToCollection, useClearHistory, useCollections, useCreateCollection, useDeleteHistory, useFavorites, useHistory, useMediaSearch, useMediaStats, formatDuration } from '../lib/api';
import Sidebar from './Sidebar';
import MobileTabBar from './MobileTabBar';
import MediaCard from './MediaCard';
import Toasts from './Toasts';

function Shell({ children, title, eyebrow }: { children: React.ReactNode; title: string; eyebrow: string }) {
    const [open, setOpen] = useState(() => window.innerWidth >= 768);
    return <div className="min-h-screen bg-dark-950 text-white"><Sidebar isOpen={open} onClose={() => setOpen(false)} /><main className={`min-h-screen transition-[margin] ${open ? 'md:ml-64' : ''}`}><header className="flex h-16 items-center gap-4 border-b border-white/[0.06] px-4 sm:px-8"><button onClick={() => setOpen((value) => !value)} className="rounded-xl p-2 text-dark-400 hover:bg-white/5 hover:text-white"><Menu className="h-5 w-5" /></button><div><p className="text-[10px] font-semibold uppercase tracking-[0.22em] text-primary-300/80">{eyebrow}</p><h1 className="text-lg font-semibold">{title}</h1></div></header><div className="p-6 pb-24 sm:p-10 md:pb-10 lg:p-16">{children}</div></main><MobileTabBar /><Toasts /></div>;
}

export function SearchPage() {
    const [params, setParams] = useSearchParams();
    const [query, setQuery] = useState(params.get('q') || '');
    const fileType = params.get('type') || undefined;
    const { data, isLoading } = useMediaSearch(query, { file_type: fileType });
    return <Shell title="Search" eyebrow="Find something to watch"><form onSubmit={(event) => { event.preventDefault(); setParams(fileType ? { q: query, type: fileType } : { q: query }); }} className="relative max-w-2xl"><Search className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-dark-600" /><input autoFocus value={query} onChange={(event) => setQuery(event.target.value)} className="input w-full py-4 pl-12 text-lg" placeholder="Search titles, files, and metadata…" /></form><div className="mt-10">{isLoading ? <p className="text-dark-400">Searching your library…</p> : (query || fileType) && data?.files.length ? <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">{data.files.map((file) => <MediaCard key={file.id} file={file} />)}</div> : <div className="rounded-2xl border border-dashed border-white/10 p-10 text-center text-dark-500">{query || fileType ? 'No results found.' : 'Start typing to search your private library.'}</div>}</div></Shell>;
}

export function FavoritesPage() {
    const { data, isLoading } = useFavorites();
    return <Shell title="Favorites" eyebrow="Your shortlist"><div className="mb-8 rounded-3xl border border-primary-400/15 bg-[radial-gradient(circle_at_80%_20%,rgba(168,85,247,.2),transparent_38%),rgba(255,255,255,.03)] p-6 sm:p-8"><p className="text-sm text-primary-200">Keep the essentials close</p><h2 className="mt-2 text-3xl font-bold">Favorites</h2><p className="mt-2 max-w-lg text-sm leading-6 text-dark-400">Media you’ve saved for the next great night in.</p></div><FavoriteGrid isLoading={isLoading} files={data || []} /></Shell>;
}

function FavoriteGrid({ isLoading, files }: { isLoading: boolean; files: any[] }) { return <div>{isLoading ? <p className="text-dark-400">Loading favorites…</p> : files.length ? <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">{files.map((file) => <MediaCard key={file.id} file={file} />)}</div> : <div className="rounded-2xl border border-dashed border-white/10 p-12 text-center text-dark-500">No favorites yet. Use the heart on a media card to save one.</div>}</div>; }

export function HistoryPage() {
    const { data, isLoading } = useHistory();
    const deleteEntry = useDeleteHistory();
    const clear = useClearHistory();
    const grouped = (data || []).reduce<Record<string, typeof data>>((groups, entry) => { const day = new Date(entry.watched_at).toLocaleDateString(undefined, { weekday: 'long', month: 'short', day: 'numeric' }); (groups[day] ||= []).push(entry); return groups; }, {});
    return <Shell title="Watch history" eyebrow="Your viewing trail"><div className="mb-8 flex flex-wrap items-end justify-between gap-4"><div><h2 className="text-3xl font-bold">Recently watched</h2><p className="mt-2 text-sm text-dark-400">A private record of what you’ve played.</p></div>{data?.length ? <button onClick={() => clear.mutate()} className="btn-secondary inline-flex items-center gap-2 text-red-300"><Trash2 className="h-4 w-4" /> Clear history</button> : null}</div>{isLoading ? <p className="text-dark-400">Loading history…</p> : Object.keys(grouped).length ? <div className="space-y-8">{Object.entries(grouped).map(([day, entries]) => <section key={day}><div className="mb-3 flex items-center gap-2 text-sm font-semibold text-dark-300"><CalendarDays className="h-4 w-4 text-primary-300" />{day}</div><div className="space-y-2">{entries?.map((entry) => <div key={entry.id} className="flex items-center gap-4 rounded-2xl border border-white/[0.07] bg-white/[0.03] p-3"><div className="h-16 w-28 overflow-hidden rounded-xl bg-dark-800">{entry.file?.thumbnail_url ? <img src={entry.file.thumbnail_url.startsWith('http') ? entry.file.thumbnail_url : `${window.location.origin}${entry.file.thumbnail_url}`} alt="" className="h-full w-full object-cover" /> : null}</div><div className="min-w-0 flex-1"><p className="truncate font-medium text-white">{entry.file?.metadata?.title || entry.file?.file_name || 'Media'}</p><p className="mt-1 flex items-center gap-2 text-xs text-dark-500"><Clock3 className="h-3 w-3" /> {new Date(entry.watched_at).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}{entry.duration ? ` · ${formatDuration(entry.duration)}` : ''}</p></div>{entry.file && <MediaCard file={entry.file} compact />}{<button onClick={() => deleteEntry.mutate(entry.id)} className="rounded-lg p-2 text-dark-600 hover:bg-red-500/10 hover:text-red-300"><Trash2 className="h-4 w-4" /></button>}</div>)}</div></section>)}</div> : <div className="rounded-2xl border border-dashed border-white/10 p-12 text-center text-dark-500">Your watch history is empty.</div>}</Shell>;
}

export function CollectionsPage() {
    const { data, isLoading } = useCollections();
    const create = useCreateCollection();
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [showForm, setShowForm] = useState(false);

    return <Shell title="Collections" eyebrow="Curate your library">
        <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
                <h2 className="text-3xl font-bold">Your collections</h2>
                <p className="mt-2 text-sm text-dark-400">Group media independently from physical folders.</p>
            </div>
            <button onClick={() => setShowForm((value) => !value)} className="btn-primary inline-flex items-center gap-2">
                <FolderPlus className="h-4 w-4" /> New collection
            </button>
        </div>
        {showForm && <form onSubmit={(event) => {
            event.preventDefault();
            if (!name.trim()) return;
            create.mutate({ name: name.trim(), description }, { onSuccess: () => { setName(''); setDescription(''); setShowForm(false); } });
        }} className="mt-6 max-w-xl rounded-2xl border border-white/[0.08] bg-white/[0.03] p-5">
            <input value={name} onChange={(event) => setName(event.target.value)} className="input w-full" placeholder="Collection name" maxLength={120} />
            <input value={description} onChange={(event) => setDescription(event.target.value)} className="input mt-3 w-full" placeholder="Short description (optional)" maxLength={500} />
            <button className="btn-primary mt-4" disabled={create.isPending}>Create collection</button>
        </form>}
        <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {isLoading ? <p className="text-dark-400">Loading collections…</p> : data?.length ? data.map((collection) => <CollectionCard key={collection.id} collection={collection} />) : <div className="col-span-full rounded-2xl border border-dashed border-white/10 p-12 text-center text-dark-500">Create a collection for movie nights, favorites, or rewatch lists.</div>}
        </div>
    </Shell>;
}

function CollectionCard({ collection }: { collection: import('../lib/api').Collection }) {
    const bulkAdd = useBulkAddToCollection();
    const [query, setQuery] = useState('');
    const [message, setMessage] = useState<string | null>(null);

    return <div className="rounded-2xl border border-white/[0.07] bg-white/[0.03] p-5">
        <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary-500/15 text-primary-200"><FolderPlus className="h-5 w-5" /></div>
        <h3 className="mt-5 text-lg font-semibold">{collection.name}</h3>
        <p className="mt-1 text-sm text-dark-400">{collection.description || 'A personal collection'}</p>
        <p className="mt-5 text-xs text-dark-500">{collection.item_count} items</p>
        <form onSubmit={(event) => {
            event.preventDefault();
            const trimmed = query.trim();
            if (!trimmed || bulkAdd.isPending) return;
            setMessage(null);
            bulkAdd.mutate({ collectionId: collection.id, query: trimmed }, {
                onSuccess: (result) => { setQuery(''); setMessage(`${result.added_count} ${result.added_count === 1 ? 'file' : 'files'} added`); },
                onError: () => setMessage('Could not add matching files'),
            });
        }} className="mt-4 border-t border-white/[0.06] pt-4">
            <label className="text-xs font-medium text-dark-400" htmlFor={`bulk-add-${collection.id}`}>Add files by filename</label>
            <div className="mt-2 flex gap-2">
                <input id={`bulk-add-${collection.id}`} value={query} onChange={(event) => setQuery(event.target.value)} className="input min-w-0 flex-1 text-sm" placeholder="e.g. S01E02 or oyuncuA" maxLength={200} />
                <button type="submit" className="btn-secondary shrink-0 px-3 text-xs" disabled={bulkAdd.isPending || !query.trim()}>{bulkAdd.isPending ? 'Adding…' : 'Add matching files'}</button>
            </div>
            {message && <p className={`mt-2 text-xs ${message.startsWith('Could') ? 'text-red-300' : 'text-emerald-300'}`}>{message}</p>}
        </form>
        {collection.files?.length ? <div className="no-scrollbar mt-4 flex gap-2 overflow-x-auto">{collection.files.slice(0, 4).map((file) => <MediaCard key={file.id} file={file} compact />)}</div> : null}
    </div>;
}

export function StatsPage() { const { data, isLoading } = useMediaStats(); return <Shell title="Statistics" eyebrow="Your media habits">{isLoading ? <p className="text-dark-400">Calculating your stats…</p> : data ? <><div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">{[['Total watched', data.total_watched], ['Movies', data.movies_watched], ['Episodes', data.episodes_watched], ['Watch time', formatDuration(data.total_watch_time)]].map(([label, value]) => <div key={String(label)} className="rounded-2xl border border-white/[0.07] bg-white/[0.03] p-5"><p className="text-xs uppercase tracking-[0.16em] text-dark-500">{label}</p><p className="mt-3 text-3xl font-bold text-white">{value}</p></div>)}</div><section className="mt-10"><div className="flex items-center gap-2"><BarChart3 className="h-5 w-5 text-primary-300" /><h2 className="text-xl font-semibold">Recent activity</h2></div><div className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-6">{data.recent_activity.map((file) => <MediaCard key={file.id} file={file} compact />)}</div></section></> : null}</Shell>; }

export function SettingsPage() {
    const [autoplay, setAutoplay] = useState(true);
    const [compact, setCompact] = useState(false);
    return <Shell title="Settings" eyebrow="Make TelePlay yours"><div className="grid max-w-4xl gap-5 lg:grid-cols-2"><SettingCard title="Playback" description="Choose how TelePlay behaves when you start watching."><Toggle label="Autoplay next episode" checked={autoplay} onChange={setAutoplay} /><Toggle label="Remember playback position" checked onChange={() => undefined} /></SettingCard><SettingCard title="Appearance" description="Tune the interface for your screen and lighting."><Toggle label="Cinematic dark theme" checked onChange={() => undefined} /><Toggle label="Compact media cards" checked={compact} onChange={setCompact} /></SettingCard><SettingCard title="Home" description="Your dashboard is built from private library activity."><p className="text-sm leading-6 text-dark-400">Sections are loaded independently and stay hidden when they have no content. More ordering controls can be added without changing your media data.</p></SettingCard><SettingCard title="Privacy" description="TelePlay keeps playback and organization data scoped to your account."><p className="text-sm leading-6 text-dark-400">Favorites, history, progress, collections, tags, and preferences are isolated by your authenticated Telegram account.</p></SettingCard></div></Shell>;
}

function SettingCard({ title, description, children }: { title: string; description: string; children: React.ReactNode }) { return <section className="rounded-2xl border border-white/[0.07] bg-white/[0.03] p-5"><h2 className="font-semibold text-white">{title}</h2><p className="mt-1 text-sm leading-6 text-dark-500">{description}</p><div className="mt-5 space-y-4">{children}</div></section>; }
function Toggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: (value: boolean) => void }) { return <label className="flex cursor-pointer items-center justify-between gap-4 text-sm text-dark-200"><span>{label}</span><button type="button" role="switch" aria-checked={checked} onClick={() => onChange(!checked)} className={`relative h-6 w-11 rounded-full transition ${checked ? 'bg-primary-500' : 'bg-dark-700'}`}><span className={`absolute top-1 h-4 w-4 rounded-full bg-white transition ${checked ? 'left-6' : 'left-1'}`} /></button></label>; }
