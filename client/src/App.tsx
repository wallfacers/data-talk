import { useState } from "react";
import {
  SidebarProvider,
  Sidebar,
  SidebarHeader,
  SidebarContent,
  SidebarFooter,
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuButton,
  SidebarTrigger,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarGroupContent,
  SidebarInset,
} from "@/components/ui/sidebar";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ChatArea } from "@/components/ChatArea";
import { QueryResult } from "@/components/QueryResult";
import type { QueryResponse } from "@/services/api";
import { Database, MessageSquare, Plus, Table } from "lucide-react";

function App() {
  const [queryResult, setQueryResult] = useState<QueryResponse | null>(null);

  return (
    <SidebarProvider>
      <Sidebar>
        <SidebarHeader>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton>
                <Database className="size-4" />
                <span>Chat Database Tool</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarHeader>

        <SidebarContent>
          <SidebarGroup>
            <SidebarGroupLabel>数据库连接</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                <SidebarMenuItem>
                  <SidebarMenuButton>
                    <Database className="size-4" />
                    <span>Demo (H2)</span>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>

          <SidebarGroup>
            <SidebarGroupLabel>会话历史</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                <SidebarMenuItem>
                  <SidebarMenuButton>
                    <MessageSquare className="size-4" />
                    <span>今日会话</span>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        </SidebarContent>

        <SidebarFooter>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton>
                <Plus className="size-4" />
                <span>新建连接</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarFooter>
      </Sidebar>

      <SidebarInset>
        <header className="flex h-12 items-center gap-2 border-b px-4">
          <SidebarTrigger />
          <h1 className="text-lg font-semibold">查询助手</h1>
        </header>
        <main className="flex h-[calc(100vh-3rem)]">
          <div className="w-1/2 border-r">
            <ChatArea onQueryResult={setQueryResult} />
          </div>
          <div className="w-1/2">
            <Tabs defaultValue="result" className="h-full">
              <div className="border-b px-4">
                <TabsList>
                  <TabsTrigger value="result">
                    <Table className="mr-1 size-3" />
                    查询结果
                  </TabsTrigger>
                </TabsList>
              </div>
              <TabsContent value="result" className="h-[calc(100%-3rem)] m-0">
                {queryResult ? (
                  <QueryResult
                    columns={queryResult.columns}
                    rows={queryResult.rows}
                    rowCount={queryResult.rowCount}
                    durationMs={queryResult.durationMs}
                  />
                ) : (
                  <div className="flex h-full items-center justify-center text-muted-foreground">
                    执行查询后结果将显示在这里
                  </div>
                )}
              </TabsContent>
            </Tabs>
          </div>
        </main>
      </SidebarInset>
    </SidebarProvider>
  );
}

export default App;
