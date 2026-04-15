import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarRail,
} from "@/components/ui/sidebar"
import { NavMain, type NavItem } from "@/features/sidebar/components/nav-main"
import { NavProjects } from "@/features/sidebar/components/nav-projects"
import { NavUser } from "@/features/sidebar/components/nav-user"
import { TeamSwitcher } from "@/features/sidebar/components/team-switcher"
import {
  GalleryVerticalEndIcon,
  TerminalSquareIcon,
  BotIcon,
  BookOpenIcon,
  Settings2Icon,
  type LucideIcon,
} from "lucide-react"

// TODO: Replace with real project data
const sidebarData = {
  user: {
    name: "Admin",
    email: "admin@example.com",
    avatar: "/avatars/admin.jpg",
  },
  teams: [
    { name: "Data Talk", logo: GalleryVerticalEndIcon, plan: "Demo" },
  ] as { name: string; logo: LucideIcon; plan: string }[],
  navMain: [
    {
      title: "Playground",
      url: "#",
      icon: <TerminalSquareIcon className="size-4" />,
      isActive: true,
      items: [
        { title: "History", url: "#" },
        { title: "Starred", url: "#" },
        { title: "Settings", url: "#" },
      ],
    },
    {
      title: "Models",
      url: "#",
      icon: <BotIcon className="size-4" />,
      items: [
        { title: "Genesis", url: "#" },
        { title: "Explorer", url: "#" },
        { title: "Quantum", url: "#" },
      ],
    },
    {
      title: "Documentation",
      url: "#",
      icon: <BookOpenIcon className="size-4" />,
      items: [
        { title: "Introduction", url: "#" },
        { title: "Get Started", url: "#" },
        { title: "Tutorials", url: "#" },
        { title: "Changelog", url: "#" },
      ],
    },
    {
      title: "Settings",
      url: "#",
      icon: <Settings2Icon className="size-4" />,
      items: [
        { title: "General", url: "#" },
        { title: "Team", url: "#" },
      ],
    },
  ] as NavItem[],
  projects: [
    { name: "Database Query", url: "#", icon: TerminalSquareIcon },
  ] as { name: string; url: string; icon: LucideIcon }[],
}

interface AppSidebarProps extends React.ComponentProps<typeof Sidebar> {}

export function AppSidebar({ ...props }: AppSidebarProps) {
  return (
    <Sidebar collapsible="icon" {...props}>
      <SidebarHeader>
        <TeamSwitcher teams={sidebarData.teams} />
      </SidebarHeader>
      <SidebarContent>
        <NavMain items={sidebarData.navMain} />
        <NavProjects projects={sidebarData.projects} />
      </SidebarContent>
      <SidebarFooter>
        <NavUser user={sidebarData.user} />
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  )
}
