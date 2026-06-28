import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { FormEvent } from "react";
import { Toaster, toast } from "sonner";

type SagaStep = {
  step: "INVENTORY" | "PAYMENT" | "SHIPMENT";
  direction: "FORWARD" | "COMPENSATION";
  status: "STARTED" | "SUCCESS" | "FAILED";
  attempt: number;
  error?: string | null;
  at: string;
};

type OrderItem = {
  sku: string;
  qty: number;
  unitPrice: string;
};

type OrderDetail = {
  orderId: string;
  customerId: string;
  totalAmount: string;
  status: string;
  state: string;
  items: OrderItem[];
  steps: SagaStep[];
  createdAt: string;
  updatedAt: string;
};

type OrderSummary = {
  orderId: string;
};

type OrdersPage = {
  content: OrderSummary[];
};

type ConnectionState = "connecting" | "connected" | "reconnecting";
type BadgeTone =
  | "pending"
  | "in-progress"
  | "success"
  | "failed"
  | "compensating"
  | "compensated";

function formatCurrency(value: string): string {
  const number = Number(value);
  if (Number.isNaN(number)) {
    return value;
  }
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2,
  }).format(number);
}

function formatDate(value: string): string {
  return new Date(value).toLocaleString();
}

function sortByUpdatedAt(items: OrderDetail[]): OrderDetail[] {
  return [...items].sort(
    (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
  );
}

function latestStep(steps: SagaStep[], stepName: SagaStep["step"]): SagaStep | null {
  const candidates = steps
    .filter((step) => step.step === stepName)
    .sort((a, b) => new Date(b.at).getTime() - new Date(a.at).getTime());
  return candidates[0] ?? null;
}

function overallTone(state: string): BadgeTone {
  if (state === "OrderConfirmed") {
    return "success";
  }
  if (state === "OrderFailed") {
    return "failed";
  }
  if (state === "Compensating") {
    return "compensating";
  }
  if (state === "ShipmentInitiated" || state === "PaymentCaptured" || state === "InventoryReserved") {
    return "in-progress";
  }
  return "pending";
}

function stepView(step: SagaStep | null): { tone: BadgeTone; label: string } {
  if (!step) {
    return { tone: "pending", label: "Pending" };
  }
  if (step.status === "STARTED") {
    return { tone: "in-progress", label: "In progress" };
  }
  if (step.status === "FAILED") {
    return { tone: "failed", label: "Failed" };
  }
  if (step.direction === "COMPENSATION") {
    return { tone: "compensated", label: "Compensated" };
  }
  return { tone: "success", label: "Success" };
}

function toneClass(tone: BadgeTone): string {
  const base = "inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium";
  if (tone === "success") {
    return `${base} border-emerald-300 bg-emerald-50 text-emerald-800 dark:border-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-200`;
  }
  if (tone === "failed") {
    return `${base} border-rose-300 bg-rose-50 text-rose-800 dark:border-rose-700 dark:bg-rose-950/50 dark:text-rose-200`;
  }
  if (tone === "compensating") {
    return `${base} border-orange-300 bg-orange-50 text-orange-800 dark:border-orange-700 dark:bg-orange-950/40 dark:text-orange-200`;
  }
  if (tone === "compensated") {
    return `${base} border-violet-300 bg-violet-50 text-violet-800 dark:border-violet-700 dark:bg-violet-950/50 dark:text-violet-200`;
  }
  if (tone === "in-progress") {
    return `${base} border-amber-300 bg-amber-50 text-amber-800 dark:border-amber-700 dark:bg-amber-950/40 dark:text-amber-200`;
  }
  return `${base} border-slate-300 bg-slate-50 text-slate-700 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300`;
}

function connectionClass(state: ConnectionState): string {
  if (state === "connected") {
    return "bg-emerald-500";
  }
  if (state === "reconnecting") {
    return "bg-amber-500 animate-pulse";
  }
  return "bg-slate-500 animate-pulse";
}

async function fetchJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `${response.status} ${response.statusText}`);
  }
  return (await response.json()) as T;
}

export default function App() {
  const [orders, setOrders] = useState<OrderDetail[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [connection, setConnection] = useState<ConnectionState>("connecting");
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);
  const [theme, setTheme] = useState<"light" | "dark">(
    document.documentElement.classList.contains("dark") ? "dark" : "light",
  );

  const [customerId, setCustomerId] = useState<string>(() => crypto.randomUUID());
  const [sku, setSku] = useState("SKU-001");
  const [qty, setQty] = useState(1);
  const [amount, setAmount] = useState("499.00");
  const [paymentFail, setPaymentFail] = useState(false);
  const [shipmentMode, setShipmentMode] = useState<"ok" | "fail" | "hang">("ok");
  const [failAfterReserve, setFailAfterReserve] = useState(false);
  const isConnectionToastOpen = useRef(false);

  const upsertOrder = useCallback((incoming: OrderDetail) => {
    setOrders((previous) => {
      const filtered = previous.filter((order) => order.orderId !== incoming.orderId);
      return sortByUpdatedAt([incoming, ...filtered]);
    });
  }, []);

  useEffect(() => {
    let alive = true;
    async function load() {
      try {
        const page = await fetchJson<OrdersPage>("/api/orders?page=0&size=30");
        const details = await Promise.all(
          page.content.map((summary) => fetchJson<OrderDetail>(`/api/orders/${summary.orderId}`)),
        );
        if (!alive) {
          return;
        }
        setOrders(sortByUpdatedAt(details));
        if (details.length > 0) {
          setSelectedOrderId(details[0].orderId);
        }
      } catch (error) {
        toast.error(`Could not load orders: ${(error as Error).message}`);
      } finally {
        if (alive) {
          setIsLoading(false);
        }
      }
    }
    void load();
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    const stream = new EventSource("/api/orders/stream");

    stream.onopen = () => {
      setConnection("connected");
      if (isConnectionToastOpen.current) {
        toast.success("Live stream reconnected");
        isConnectionToastOpen.current = false;
      }
    };

    const listener = (event: Event) => {
      const message = event as MessageEvent<string>;
      try {
        const payload = JSON.parse(message.data) as OrderDetail;
        upsertOrder(payload);
      } catch {
        // Ignore malformed event payloads from transient stream noise.
      }
    };

    stream.addEventListener("order-update", listener);
    stream.onerror = () => {
      setConnection((current) => (current === "reconnecting" ? current : "reconnecting"));
      if (!isConnectionToastOpen.current) {
        toast.warning("Connection lost. Reconnecting to live stream...");
        isConnectionToastOpen.current = true;
      }
    };

    return () => {
      stream.removeEventListener("order-update", listener);
      stream.close();
    };
  }, [upsertOrder]);

  const selectedOrder = useMemo(() => {
    return orders.find((order) => order.orderId === selectedOrderId) ?? null;
  }, [orders, selectedOrderId]);

  const stats = useMemo(() => {
    const total = orders.length;
    const confirmed = orders.filter((order) => order.state === "OrderConfirmed").length;
    const failed = orders.filter((order) => order.state === "OrderFailed").length;
    const inflight = orders.filter(
      (order) => order.state !== "OrderConfirmed" && order.state !== "OrderFailed",
    ).length;
    return { total, confirmed, failed, inflight };
  }, [orders]);

  function toggleTheme() {
    const nextTheme = theme === "light" ? "dark" : "light";
    setTheme(nextTheme);
    localStorage.setItem("orderflow-theme", nextTheme);
    if (nextTheme === "dark") {
      document.documentElement.classList.add("dark");
    } else {
      document.documentElement.classList.remove("dark");
    }
  }

  async function submitOrder(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    const requestBody = {
      customerId,
      items: [{ sku, qty: Number(qty) }],
      amount: Number(amount),
      simulate: {
        paymentFail: paymentFail || undefined,
        shipmentMode: shipmentMode !== "ok" ? shipmentMode : undefined,
        failAfterReserve: failAfterReserve || undefined,
      },
    };

    try {
      const order = await fetchJson<{ orderId: string; status: string }>("/api/orders", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": crypto.randomUUID(),
        },
        body: JSON.stringify(requestBody),
      });
      const detail = await fetchJson<OrderDetail>(`/api/orders/${order.orderId}`);
      upsertOrder(detail);
      setSelectedOrderId(order.orderId);
      toast.success(`Order ${order.orderId.slice(0, 8)} submitted`);
      setCustomerId(crypto.randomUUID());
    } catch (error) {
      toast.error(`Order submission failed: ${(error as Error).message}`);
    }
  }

  async function runConcurrentScenario() {
    const ordersToCreate = 20;
    const results = await Promise.allSettled(
      Array.from({ length: ordersToCreate }).map(() =>
        fetchJson<{ orderId: string }>("/api/orders", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Idempotency-Key": crypto.randomUUID(),
          },
          body: JSON.stringify({
            customerId: crypto.randomUUID(),
            items: [{ sku, qty: 1 }],
            amount: Number(amount),
            simulate: {
              paymentFail: false,
              shipmentMode: "ok",
              failAfterReserve: false,
            },
          }),
        }),
      ),
    );
    const success = results.filter((result) => result.status === "fulfilled").length;
    const failed = ordersToCreate - success;
    toast.info(`Concurrent run complete: ${success} accepted, ${failed} failed`);
  }

  return (
    <div className="min-h-screen bg-background text-foreground">
      <Toaster richColors position="top-right" />

      <header className="sticky top-0 z-20 border-b border-border/70 bg-card/95 backdrop-blur">
        <div className="mx-auto flex w-full max-w-7xl items-center justify-between gap-3 px-4 py-3 2xl:max-w-[1440px]">
          <div className="flex items-center gap-3">
            <img alt="OrderFlow logo" className="h-9 w-9 rounded-md border border-border" src="/logo.png" />
            <div>
              <p className="text-sm font-semibold tracking-tight">OrderFlow</p>
              <p className="text-xs text-muted-foreground">Saga orchestration dashboard</p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <span className="inline-flex items-center gap-2 rounded-full border border-border px-2 py-1 text-xs">
              <span className={`h-2 w-2 rounded-full ${connectionClass(connection)}`} />
              {connection === "connected" ? "Live connected" : connection === "reconnecting" ? "Reconnecting" : "Connecting"}
            </span>
            <button
              className="rounded-md border border-border px-3 py-2 text-xs hover:bg-muted"
              onClick={toggleTheme}
              type="button"
            >
              {theme === "light" ? "Dark mode" : "Light mode"}
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto flex w-full max-w-7xl flex-col gap-6 px-4 py-6 2xl:max-w-[1440px]">
        <section className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <StatCard label="Total orders" value={stats.total} />
          <StatCard label="Confirmed" value={stats.confirmed} tone="success" />
          <StatCard label="Failed" value={stats.failed} tone="failed" />
          <StatCard label="In-flight" value={stats.inflight} tone="in-progress" />
        </section>

        <section className="animate-fade-in rounded-xl border border-border bg-card p-4 shadow-sm">
          <h2 className="mb-4 text-base font-semibold">Demo Controls</h2>
          <form className="grid gap-3 lg:grid-cols-5" onSubmit={submitOrder}>
            <label className="col-span-2 flex flex-col gap-1 text-sm">
              Customer ID
              <input
                className="h-11 rounded-md border border-border bg-background px-3 font-mono text-xs"
                onChange={(event) => setCustomerId(event.target.value)}
                required
                value={customerId}
              />
            </label>
            <label className="flex flex-col gap-1 text-sm">
              SKU
              <input
                className="h-11 rounded-md border border-border bg-background px-3"
                onChange={(event) => setSku(event.target.value)}
                required
                value={sku}
              />
            </label>
            <label className="flex flex-col gap-1 text-sm">
              Qty
              <input
                className="h-11 rounded-md border border-border bg-background px-3"
                min={1}
                onChange={(event) => setQty(Number(event.target.value))}
                required
                type="number"
                value={qty}
              />
            </label>
            <label className="flex flex-col gap-1 text-sm">
              Amount
              <input
                className="h-11 rounded-md border border-border bg-background px-3"
                min="0.01"
                onChange={(event) => setAmount(event.target.value)}
                required
                step="0.01"
                type="number"
                value={amount}
              />
            </label>

            <label className="inline-flex items-center gap-2 text-sm">
              <input checked={paymentFail} onChange={(event) => setPaymentFail(event.target.checked)} type="checkbox" />
              Force payment fail
            </label>
            <label className="inline-flex items-center gap-2 text-sm">
              <input
                checked={failAfterReserve}
                onChange={(event) => setFailAfterReserve(event.target.checked)}
                type="checkbox"
              />
              Fail after reserve
            </label>
            <label className="col-span-2 flex flex-col gap-1 text-sm">
              Shipment mode
              <select
                className="h-11 rounded-md border border-border bg-background px-3"
                onChange={(event) => setShipmentMode(event.target.value as "ok" | "fail" | "hang")}
                value={shipmentMode}
              >
                <option value="ok">ok</option>
                <option value="fail">fail</option>
                <option value="hang">hang</option>
              </select>
            </label>
            <div className="col-span-2 flex flex-wrap gap-2">
              <button className="h-11 rounded-md bg-accent px-4 text-sm font-medium text-white" type="submit">
                Place order
              </button>
              <button
                className="h-11 rounded-md border border-border px-4 text-sm font-medium hover:bg-muted"
                onClick={runConcurrentScenario}
                type="button"
              >
                Run 20 concurrent
              </button>
            </div>
          </form>
        </section>

        <section className="animate-fade-in rounded-xl border border-border bg-card p-4 shadow-sm">
          <h2 className="mb-4 text-base font-semibold">Live Orders</h2>
          {isLoading ? (
            <SkeletonTable />
          ) : orders.length === 0 ? (
            <p className="rounded-lg border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
              No orders yet. Use Demo Controls to create your first saga.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full text-left text-sm">
                <thead className="text-xs uppercase tracking-wide text-muted-foreground">
                  <tr>
                    <th className="pb-3 pr-4">Order</th>
                    <th className="pb-3 pr-4">Customer</th>
                    <th className="pb-3 pr-4">Amount</th>
                    <th className="pb-3 pr-4">Inventory</th>
                    <th className="pb-3 pr-4">Payment</th>
                    <th className="pb-3 pr-4">Shipment</th>
                    <th className="pb-3 pr-4">Overall</th>
                    <th className="pb-3">Updated</th>
                  </tr>
                </thead>
                <tbody>
                  {orders.map((order) => {
                    const inventory = stepView(latestStep(order.steps, "INVENTORY"));
                    const payment = stepView(latestStep(order.steps, "PAYMENT"));
                    const shipment = stepView(latestStep(order.steps, "SHIPMENT"));
                    return (
                      <tr
                        className={`cursor-pointer border-t border-border/60 transition hover:bg-muted/60 ${
                          selectedOrderId === order.orderId ? "bg-muted/60" : ""
                        }`}
                        key={order.orderId}
                        onClick={() => setSelectedOrderId(order.orderId)}
                      >
                        <td className="max-w-[150px] break-all py-3 pr-4 font-mono text-xs">{order.orderId}</td>
                        <td className="max-w-[150px] break-all py-3 pr-4 font-mono text-xs">{order.customerId}</td>
                        <td className="py-3 pr-4 tabular-nums">{formatCurrency(order.totalAmount)}</td>
                        <td className="py-3 pr-4">
                          <span className={toneClass(inventory.tone)}>{inventory.label}</span>
                        </td>
                        <td className="py-3 pr-4">
                          <span className={toneClass(payment.tone)}>{payment.label}</span>
                        </td>
                        <td className="py-3 pr-4">
                          <span className={toneClass(shipment.tone)}>{shipment.label}</span>
                        </td>
                        <td className="py-3 pr-4">
                          <span className={toneClass(overallTone(order.state))}>{order.state}</span>
                        </td>
                        <td className="py-3 text-xs text-muted-foreground">{formatDate(order.updatedAt)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <section className="animate-fade-in rounded-xl border border-border bg-card p-4 shadow-sm">
          <h2 className="mb-4 text-base font-semibold">Order Detail</h2>
          {selectedOrder ? (
            <div className="space-y-4">
              <div className="grid gap-3 md:grid-cols-3">
                <InfoItem label="Order ID" value={selectedOrder.orderId} />
                <InfoItem label="Correlation ID" value={selectedOrder.orderId} />
                <InfoItem label="State" value={selectedOrder.state} />
              </div>
              <div className="rounded-lg border border-border">
                <div className="border-b border-border px-4 py-2 text-sm font-medium">Saga Timeline</div>
                <ul className="max-h-[320px] overflow-auto">
                  {selectedOrder.steps
                    .slice()
                    .sort((a, b) => new Date(a.at).getTime() - new Date(b.at).getTime())
                    .map((step) => (
                      <li className="flex items-start justify-between gap-4 border-b border-border/60 px-4 py-3" key={`${step.step}-${step.at}-${step.direction}`}>
                        <div className="space-y-1">
                          <p className="text-sm font-medium">
                            {step.step} - {step.direction}
                          </p>
                          <p className="text-xs text-muted-foreground">Attempt {step.attempt}</p>
                          {step.error ? <p className="text-xs text-rose-600 dark:text-rose-300">{step.error}</p> : null}
                        </div>
                        <div className="space-y-1 text-right">
                          <span className={toneClass(stepView(step).tone)}>{step.status}</span>
                          <p className="text-xs text-muted-foreground">{formatDate(step.at)}</p>
                        </div>
                      </li>
                    ))}
                </ul>
              </div>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">Select an order row to inspect its timeline.</p>
          )}
        </section>
      </main>
    </div>
  );
}

function StatCard(props: { label: string; value: number; tone?: BadgeTone }) {
  const tone = props.tone ?? "pending";
  return (
    <article className="animate-fade-in rounded-xl border border-border bg-card p-4 shadow-sm">
      <p className="text-xs uppercase tracking-wide text-muted-foreground">{props.label}</p>
      <p className={`mt-1 text-2xl font-semibold tabular-nums ${toneClass(tone)} inline-flex`}>{props.value}</p>
    </article>
  );
}

function InfoItem(props: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border p-3">
      <p className="text-xs uppercase tracking-wide text-muted-foreground">{props.label}</p>
      <p className="mt-1 break-all font-mono text-xs">{props.value}</p>
    </div>
  );
}

function SkeletonTable() {
  return (
    <div className="space-y-2">
      {Array.from({ length: 6 }).map((_, index) => (
        <div className="h-10 animate-pulse rounded-md bg-muted" key={index} />
      ))}
    </div>
  );
}
