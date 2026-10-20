const $ = (selector) => document.querySelector(selector);
let classes = [];
let endpoints = [];
let jarName = "";
const esc = (value) => String(value ?? "").replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const toast = (message) => {
  const el = $("#toast"); el.textContent = message; el.classList.add("show");
  setTimeout(() => el.classList.remove("show"), 2600);
};
async function api(url, options={}) {
  const response = await fetch(url, options);
  const text = await response.text();
  let data;
  try { data = text ? JSON.parse(text) : {}; } catch { data = {detail:text}; }
  if (!response.ok) throw new Error(data.error || data.detail || `Request failed (${response.status})`);
  return data;
}
function updateStats() {
  $("#statJar").textContent = jarName || "None";
  $("#statClasses").textContent = classes.length;
  $("#statEndpoints").textContent = endpoints.length;
  $("#classCount").textContent = `${classes.length} classes`;
}
$("#browse").addEventListener("click", () => $("#jarFile").click());
$("#jarFile").addEventListener("change", () => {
  const file = $("#jarFile").files[0];
  if (file) $(".upload-zone strong").textContent = file.name;
});
$("#uploadForm").addEventListener("submit", async event => {
  event.preventDefault();
  const file = $("#jarFile").files[0];
  if (!file) { toast("Choose a JAR file first."); return; }
  if (!file.name.toLowerCase().endsWith(".jar")) { toast("Please select a .jar file."); return; }
  const button = $("#uploadButton"); button.disabled = true; button.textContent = "Scanning archive…";
  $("#uploadMessage").className = "message"; $("#uploadMessage").textContent = "Uploading and scanning class metadata…";
  try {
    const form = new FormData(); form.append("jar", file);
    const result = await api("/api/upload", {method:"POST", body:form});
    classes = result.classes || []; endpoints = []; jarName = file.name;
    $("#uploadMessage").className = "message success";
    $("#uploadMessage").textContent = `${result.message} Found ${classes.length} class(es).`;
    renderClasses(); renderEndpoints(); updateStats(); toast("JAR scanned successfully.");
  } catch (e) {
    $("#uploadMessage").className = "message error"; $("#uploadMessage").textContent = e.message;
  } finally { button.disabled = false; button.innerHTML = 'Scan archive <span>→</span>'; }
});
$("#search").addEventListener("input", renderClasses);
function renderClasses() {
  const query = $("#search").value.trim().toLowerCase();
  const shown = classes.map(c => ({...c, methods:(c.methods||[]).filter(m =>
    `${c.className} ${m.name} ${m.parameterTypes.join(" ")} ${m.returnType}`.toLowerCase().includes(query)
  )})).filter(c => c.methods.length && (query || true));
  if (!classes.length) {
    $("#classList").className = "empty-state";
    $("#classList").innerHTML = '<div class="empty-icon">⌁</div><strong>No classes scanned yet</strong><p>Upload a JAR above to populate the explorer.</p>'; return;
  }
  if (!shown.length) {
    $("#classList").className = "empty-state";
    $("#classList").innerHTML = '<div class="empty-icon">⌕</div><strong>No matching methods</strong><p>Try another search term.</p>'; return;
  }
  $("#classList").className = "";
  $("#classList").innerHTML = shown.map((c, index) => `<details class="class-card" ${index===0?"open":""}>
    <summary><span class="class-glyph">C</span><span style="flex:1"><div class="class-title">${esc(c.className)}</div><div class="class-meta">${c.methods.length} matching method(s) · extends ${esc(c.superName||"—")}</div></span><span class="badge">${c.interface?"INTERFACE":c.abstract?"ABSTRACT":"CLASS"}</span></summary>
    ${(c.methods||[]).map(m => `<div class="method-row"><div class="method-info"><div class="method-name">${esc(m.name)} <span class="badge">${esc(m.returnType)}</span></div><div class="method-signature">(${m.parameterTypes.map(esc).join(", ")})</div><div class="method-badges"><span class="badge ${m.public?"good":""}">${m.public?"PUBLIC":"NON-PUBLIC"}</span><span class="badge ${m.static?"good":""}">${m.static?"STATIC":"INSTANCE"}</span>${m.eligible?'<span class="badge good">ELIGIBLE</span>':'<span class="badge warn">NOT ELIGIBLE</span>'}</div></div>
    <button class="button small ${m.eligible?"primary":"secondary"}" ${m.eligible?"":"disabled"} data-class="${esc(c.className)}" data-method="${esc(m.name)}" data-descriptor="${esc(m.descriptor)}">${m.eligible?"Expose method":"Unavailable"}</button></div>`).join("")}
    </details>`).join("");
  $("#classList").querySelectorAll("button[data-method]").forEach(button => button.addEventListener("click", () => expose(button)));
}
async function expose(button) {
  button.disabled = true; button.textContent = "Exposing…";
  try {
    const endpoint = await api("/api/expose", {method:"POST", headers:{"Content-Type":"application/json"},
      body:JSON.stringify({className:button.dataset.class, methodName:button.dataset.method, descriptor:button.dataset.descriptor})});
    endpoints.push(endpoint); renderEndpoints(); updateStats(); toast("Endpoint created: " + endpoint.path);
  } catch (e) { toast(e.message); }
  finally { button.disabled = false; button.textContent = "Expose method"; }
}
function renderEndpoints() {
  if (!endpoints.length) {
    $("#endpointList").className = "empty-state compact";
    $("#endpointList").innerHTML = '<div class="empty-icon">⇄</div><strong>No endpoints yet</strong><p>Select an eligible method and expose it to create your first route.</p>'; return;
  }
  $("#endpointList").className = "";
  $("#endpointList").innerHTML = endpoints.map(e => `<div class="endpoint-card">
    <div class="endpoint-top"><span class="verb">POST</span><code class="endpoint-path">${esc(e.path)}</code><button class="button small secondary" data-copy="${esc(e.path)}">Copy path</button></div>
    <div class="endpoint-desc">${esc(e.className)}.${esc(e.methodName)}(${e.parameterTypes.map(esc).join(", ")}) → ${esc(e.returnType)}</div>
    <form class="endpoint-form" data-id="${esc(e.id)}"><label>JSON request body <span style="color:#6f7786">· args array in declaration order</span></label>
      <textarea spellcheck="false" aria-label="JSON request body">${JSON.stringify({args:e.parameterTypes.map(t=>t==="String"?"text":t==="boolean"||t==="java.lang.Boolean"?true:t==="char"||t==="java.lang.Character"?"x":0)},null,2)}</textarea>
      <div class="endpoint-actions"><button class="button primary small" type="submit">Send request →</button><span class="count">Content-Type: application/json</span></div><div class="result" hidden></div>
    </form></div>`).join("");
  $("#endpointList").querySelectorAll("form[data-id]").forEach(form => form.addEventListener("submit", invokeEndpoint));
  $("#endpointList").querySelectorAll("button[data-copy]").forEach(button => button.addEventListener("click", () => {
    navigator.clipboard?.writeText(location.origin + button.dataset.copy).then(()=>toast("Endpoint URL copied.")).catch(()=>toast("Path: "+button.dataset.copy));
  }));
}
async function invokeEndpoint(event) {
  event.preventDefault();
  const form = event.currentTarget, button = form.querySelector("button[type=submit]"), result = form.querySelector(".result");
  button.disabled = true; button.textContent = "Sending…"; result.hidden = false; result.className = "result"; result.textContent = "Waiting for response…";
  try {
    const body = JSON.parse(form.querySelector("textarea").value);
    const data = await api("/generated/" + encodeURIComponent(form.dataset.id), {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify(body)});
    result.textContent = JSON.stringify(data,null,2);
  } catch (e) { result.className = "result error"; result.textContent = e.message; }
  finally { button.disabled = false; button.textContent = "Send request →"; }
}
(async () => {
  try {
    const status = await api("/api/status");
    if (status.javaVersion < 25) toast("This project targets Java 25; detected Java " + status.javaVersion + ".");
  } catch (e) { console.warn("API status unavailable", e); }
})();
