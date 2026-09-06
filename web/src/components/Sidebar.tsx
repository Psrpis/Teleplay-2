import { BarChart3, Clock3, Film, FolderOpen, Heart, History, Home, LogOut, HardDrive, Settings, Tv, Users, X } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import logo from '../assets/logo.png';
import { useAppStore } from '../lib/store';
import { useStorageStats, formatFileSize, useLogoutAll } from '../lib/api';
import { useState } from 'react';

interface SidebarProps { isOpen: boolean; onClose: () => void; }
type LibrarySection = 'files' | 'recent' | 'continue_watching';

export default function Sidebar({ isOpen, onClose }: SidebarProps) {
    const { activeSection, setActiveSection } = useAppStore();
    const { data: storage } = useStorageStats();
    const location = useLocation();
    const navigate = useNavigate();
    const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
    const [showLogoutAllConfirm, setShowLogoutAllConfirm] = useState(false);
    const logoutAllMutation = useLogoutAll();

    const go = (path: string, section?: LibrarySection) => {
        if (section) setActiveSection(section);
        navigate(path);
        onClose();
    };
    const isPath = (path: string) => path === '/' ? location.pathname === '/' : location.pathname.startsWith(path);

    const NavItem = ({ icon: Icon, label, path, section }: { icon: typeof Home; label: string; path: string; section?: LibrarySection }) => {
        const active = section ? location.pathname.startsWith('/library') && activeSection === section : isPath(path);
        return (
            <button onClick={() => go(path, section)} className={`group flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition ${active ? 'bg-primary-500/15 text-primary-200 shadow-inner shadow-primary-500/10' : 'text-dark-400 hover:bg-white/[0.05] hover:text-white'}`}>
                <Icon className={`h-4.5 w-4.5 ${active ? 'text-primary-300' : 'text-dark-500 group-hover:text-dark-200'}`} />
                {label}
            </button>
        );
    };

    const logout = () => {
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
        localStorage.removeItem('user');
        window.location.href = '/login';
    };

    const logoutAll = async () => {
        try { await logoutAllMutation.mutateAsync(); } finally { logout(); }
    };

    return (
        <>
            <div className={`fixed inset-0 z-40 bg-black/60 backdrop-blur-sm md:hidden ${isOpen ? 'opacity-100' : 'pointer-events-none opacity-0'} transition-opacity`} onClick={onClose} />
            <aside className={`fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r border-white/[0.07] bg-dark-950/95 px-3 shadow-2xl shadow-black/40 backdrop-blur-2xl transition-transform duration-300 ${isOpen ? 'translate-x-0' : '-translate-x-full'}`}>
                <div className="flex items-center justify-between px-3 py-5">
                    <button className="flex items-center gap-3" onClick={() => go('/')}>
                        <img src={logo} alt="TelePlay" className="h-9 w-9 rounded-xl shadow-lg shadow-primary-500/20" />
                        <span className="text-lg font-bold tracking-tight text-white">Tele<span className="text-primary-300">Play</span></span>
                    </button>
                    <button onClick={onClose} className="rounded-lg p-2 text-dark-500 hover:bg-white/5 hover:text-white md:hidden"><X className="h-5 w-5" /></button>
                </div>
                <nav className="flex-1 space-y-1 overflow-y-auto pb-4">
                    <p className="px-3 pb-2 pt-3 text-[10px] font-semibold uppercase tracking-[0.2em] text-dark-600">Discover</p>
                    <NavItem icon={Home} label="Home" path="/" />
                    <NavItem icon={Film} label="Movies" path="/search?type=video" />
                    <NavItem icon={Tv} label="Series" path="/search?type=series" />
                    <NavItem icon={Heart} label="Favorites" path="/favorites" />
                    <p className="px-3 pb-2 pt-6 text-[10px] font-semibold uppercase tracking-[0.2em] text-dark-600">Your library</p>
                    <NavItem icon={FolderOpen} label="My Files" path="/library" section="files" />
                    <NavItem icon={Clock3} label="Recently Added" path="/library" section="recent" />
                    <NavItem icon={History} label="Continue Watching" path="/library" section="continue_watching" />
                    <NavItem icon={History} label="Watch History" path="/history" />
                    <NavItem icon={FolderOpen} label="Collections" path="/collections" />
                    <NavItem icon={BarChart3} label="Statistics" path="/stats" />
                </nav>
                <div className="mb-3 rounded-2xl border border-white/[0.06] bg-white/[0.03] p-3">
                    <div className="mb-2 flex items-center gap-2 text-xs font-medium text-dark-300"><HardDrive className="h-3.5 w-3.5 text-primary-300" /> Private library</div>
                    <p className="text-lg font-semibold text-white">{storage ? formatFileSize(storage.total_size) : '—'}</p>
                    <p className="mt-1 text-[11px] text-dark-500">Stored in your Telegram archive</p>
                </div>
                <div className="space-y-1 border-t border-white/[0.07] py-3">
                    <NavItem icon={Settings} label="Settings" path="/settings" />
                    <button onClick={() => setShowLogoutConfirm(true)} className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-dark-500 transition hover:bg-red-500/10 hover:text-red-300"><LogOut className="h-4.5 w-4.5" /> Logout</button>
                    <button onClick={() => setShowLogoutAllConfirm(true)} className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-dark-600 transition hover:bg-orange-500/10 hover:text-orange-300"><Users className="h-4.5 w-4.5" /> Logout all devices</button>
                </div>
            </aside>
            {showLogoutConfirm && <ConfirmDialog title="End this session?" body="You can sign back in from any device with a new login code." confirm="Logout" tone="red" onClose={() => setShowLogoutConfirm(false)} onConfirm={logout} />}
            {showLogoutAllConfirm && <ConfirmDialog title="Logout everywhere?" body="This invalidates all active sessions for your account." confirm={logoutAllMutation.isPending ? 'Logging out…' : 'Logout all'} tone="orange" onClose={() => setShowLogoutAllConfirm(false)} onConfirm={logoutAll} />}
        </>
    );
}

function ConfirmDialog({ title, body, confirm, tone, onClose, onConfirm }: { title: string; body: string; confirm: string; tone: 'red' | 'orange'; onClose: () => void; onConfirm: () => void }) {
    return <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm"><div className="w-full max-w-sm rounded-2xl border border-white/10 bg-dark-900 p-6 shadow-2xl"><h3 className="text-lg font-semibold text-white">{title}</h3><p className="mt-2 text-sm leading-6 text-dark-400">{body}</p><div className="mt-6 flex gap-3"><button onClick={onClose} className="btn-secondary flex-1">Cancel</button><button onClick={onConfirm} className={`flex-1 rounded-lg px-4 py-2 font-medium text-white ${tone === 'red' ? 'bg-red-500 hover:bg-red-400' : 'bg-orange-500 hover:bg-orange-400'}`}>{confirm}</button></div></div></div>;
}
