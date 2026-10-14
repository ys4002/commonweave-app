import { Route, Routes } from 'react-router-dom';
import { Navigate, Outlet } from 'react-router-dom';
import { PublicOnly, RequireAuth, useAuth } from './auth/auth';
import { AppShell } from './components/AppShell';
import { LoginPage, SignupPage } from './pages/AuthPages';
import { CircleDetailPage, CirclesPage } from './pages/CirclePages';
import { DashboardPage } from './pages/DashboardPage';
import { ExplorePage } from './pages/ExplorePage';
import { HandoffDetailPage, HandoffsPage } from './pages/HandoffPages';
import { ImpactPage } from './pages/ImpactPage';
import { ListingDetailPage } from './pages/ListingDetailPage';
import { ListingFormPage } from './pages/ListingFormPage';
import { ModerationPage } from './pages/ModerationPage';
import { NotFoundPage, NotificationsPage, ProfilePage } from './pages/NotificationsProfilePages';
import {
  AdminAuditPage,
  AdminCirclesPage,
  AdminListingsPage,
  AdminOverviewPage,
  AdminReportsPage,
  AdminUserDetailPage,
  AdminUsersPage,
  ForbiddenPage,
} from './pages/AdminPages';
import { OnboardingPage } from './pages/OnboardingPage';

function Home() {
  const { user } = useAuth();
  return user?.platformRole === 'ADMIN' ? <Navigate to="/admin" replace /> : <DashboardPage />;
}

function AdminOnly() {
  const { user } = useAuth();
  return user?.platformRole === 'ADMIN' ? <Outlet /> : <ForbiddenPage />;
}

export function App() {
  return (
    <Routes>
      <Route element={<PublicOnly />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
      </Route>
      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route path="/onboarding" element={<OnboardingPage />} />
          <Route path="/" element={<Home />} />
          <Route path="/explore" element={<ExplorePage />} />
          <Route path="/listings/new" element={<ListingFormPage />} />
          <Route path="/listings/:id" element={<ListingDetailPage />} />
          <Route path="/listings/:id/edit" element={<ListingFormPage />} />
          <Route path="/handoffs" element={<HandoffsPage />} />
          <Route path="/handoffs/:id" element={<HandoffDetailPage />} />
          <Route path="/circles" element={<CirclesPage />} />
          <Route path="/circles/:id" element={<CircleDetailPage />} />
          <Route path="/circles/:id/moderate" element={<ModerationPage />} />
          <Route path="/impact" element={<ImpactPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/admin" element={<AdminOnly />}>
            <Route index element={<AdminOverviewPage />} />
            <Route path="users" element={<AdminUsersPage />} />
            <Route path="users/:id" element={<AdminUserDetailPage />} />
            <Route path="circles" element={<AdminCirclesPage />} />
            <Route path="listings" element={<AdminListingsPage />} />
            <Route path="reports" element={<AdminReportsPage />} />
            <Route path="audit" element={<AdminAuditPage />} />
          </Route>
          <Route path="/me" element={<ProfilePage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
