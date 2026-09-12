import { History, Heart, Home, Menu, Search } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import Sidebar from './Sidebar';

const TABS = [
    { path: '/', icon: Home, label: 'Home' },
    { path: '/search', icon: Search, label: 'Search' },
    { path: '/favorites', icon: Heart, label: 'Favorites' },
    { path: '/history', icon: History, label: 'History' },
];

export default function MobileTabBar() {
    const location = useLocation();
    const navigate = useNavigate();
    const [moreOpen, setMoreOpen] = useState(false);

    const isActive = (path: string) =>
        path === '/' ? location.pathname === '/' : location.pathname.startsWith(path);

    return (
        <>
            <nav
                className="fixed bottom-0 left-0 right-0 z-30 flex items-stretch border-t border-white/[0.07] bg-dark-950/95 backdrop-blur-xl md:hidden"
                style={{ paddingBottom: 'env(safe-area-inset-bottom, 0px)' }}
            >
                {TABS.map(({ path, icon: Icon, label }) => {
                    const active = isActive(path);
                    return (
                        <button
                            key={path}
                            onClick={() => navigate(path)}
                            className="flex min-w-0 flex-1 flex-col items-center justify-center gap-0.5 py-2.5"
                        >
                            <Icon
                                className={`h-5 w-5 transition-colors ${active ? 'text-primary-300' : 'text-dark-500'}`}
                                fill={label === 'Favorites' && active ? 'currentColor' : 'none'}
                            />
                            <span className={`truncate text-[10px] font-medium transition-colors ${active ? 'text-primary-300' : 'text-dark-500'}`}>
                                {label}
                            </span>
                        </button>
                    );
                })}
                <button
                    onClick={() => setMoreOpen(true)}
                    className="flex min-w-0 flex-1 flex-col items-center justify-center gap-0.5 py-2.5"
                >
                    <Menu className="h-5 w-5 text-dark-500" />
                    <span className="text-[10px] font-medium text-dark-500">More</span>
                </button>
            </nav>
            {moreOpen && <Sidebar isOpen={moreOpen} onClose={() => setMoreOpen(false)} />}
        </>
    );
}
