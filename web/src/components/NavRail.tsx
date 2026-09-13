/**
 * NavRail - a persistent left-edge icon rail (52px), visible on every
 * screen size. Replaces the old bottom tab bar (MobileTabBar). Primary
 * routes live directly on the rail; everything else (Movies, Series,
 * Actors, My Files, Collections, Stats, Settings, Logout) stays reachable
 * through the existing Sidebar drawer via the "More" icon.
 *
 * Self-contained and route-aware — drop `<NavRail />` into any page
 * layout with no props.
 */
import { BarChart3, Film, Heart, History, Home, Menu, Search, Settings, Tv, Users } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import Sidebar from './Sidebar';

const RAIL_ITEMS = [
    { path: '/', icon: Home, label: 'Home' },
    { path: '/search', icon: Search, label: 'Search' },
    { path: '/library', icon: Film, label: 'Library' },
    { path: '/favorites', icon: Heart, label: 'Favorites' },
    { path: '/history', icon: History, label: 'History' },
    { path: '/series', icon: Tv, label: 'Series' },
    { path: '/actors', icon: Users, label: 'Actors' },
    { path: '/stats', icon: BarChart3, label: 'Statistics' },
];

export const RAIL_WIDTH = 52;

export default function NavRail() {
    const location = useLocation();
    const navigate = useNavigate();
    const [moreOpen, setMoreOpen] = useState(false);

    const isActive = (path: string) =>
        path === '/' ? location.pathname === '/' : location.pathname.startsWith(path);

    return (
        <>
            <nav
                className="fixed inset-y-0 left-0 z-30 flex flex-col items-center gap-3 border-r border-white/[0.07] bg-dark-950/95 py-4 backdrop-blur-xl"
                style={{ width: RAIL_WIDTH, paddingBottom: 'env(safe-area-inset-bottom, 12px)' }}
            >
                <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-3">
                {RAIL_ITEMS.map(({ path, icon: Icon, label }) => {
                    const active = isActive(path);
                    return (
                        <button
                            key={path}
                            onClick={() => navigate(path)}
                            aria-label={label}
                            title={label}
                            className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors"
                        >
                            <Icon
                                className={`h-[19px] w-[19px] transition-colors ${active ? 'text-primary-300' : 'text-dark-500 hover:text-dark-300'}`}
                                fill={label === 'Favorites' && active ? 'currentColor' : 'none'}
                            />
                        </button>
                    );
                })}
                </div>
                <button
                    onClick={() => setMoreOpen(true)}
                    aria-label="More"
                    title="More"
                    className="flex h-8 w-8 items-center justify-center rounded-lg text-dark-500 transition-colors hover:text-dark-300"
                >
                    <Menu className="h-[19px] w-[19px]" />
                </button>
                <button
                    onClick={() => navigate('/settings')}
                    aria-label="Settings"
                    title="Settings"
                    className="flex h-8 w-8 items-center justify-center rounded-lg text-dark-600 transition-colors hover:text-dark-300"
                >
                    <Settings className="h-[18px] w-[18px]" />
                </button>
            </nav>
            {moreOpen && <Sidebar isOpen={moreOpen} onClose={() => setMoreOpen(false)} />}
        </>
    );
}
