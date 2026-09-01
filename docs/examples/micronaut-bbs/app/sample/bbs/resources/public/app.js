const state = {
  token: localStorage.getItem("norm-bbs-session") || "",
  username: localStorage.getItem("norm-bbs-user") || "",
  boards: [],
  selectedBoard: null,
  page: 0,
  totalPages: 1,
  currentTopic: null
}

const elements = Object.fromEntries([
  "account", "authPanel", "notice", "boards", "boardForm", "boardSelect", "topics",
  "composeButton", "composeDialog", "topicForm", "topicDialog", "topicTitle", "topicMeta",
  "topicContent", "replies", "replyCount", "replyForm", "pageLabel", "previousPage", "nextPage",
  "closeTopic"
].map(id => [id, document.getElementById(id)]))

async function api(path, options = {}) {
  const headers = { Accept: "application/json", ...options.headers }
  if (options.body) headers["Content-Type"] = "application/json"
  if (state.token) headers["X-Session"] = state.token
  const response = await fetch(`/bbs${path}`, { ...options, headers })
  if (!response.ok) throw new Error(response.status === 401 ? "请先登录" : `请求失败（${response.status}）`)
  const text = await response.text()
  if (!text) return null
  try { return JSON.parse(text) } catch { return text }
}

function notify(message, error = false) {
  elements.notice.textContent = message
  elements.notice.style.borderLeftColor = error ? "var(--accent)" : "var(--green)"
  elements.notice.hidden = false
  window.setTimeout(() => { elements.notice.hidden = true }, 3200)
}

function setSession(username, token) {
  state.username = username
  state.token = token
  localStorage.setItem("norm-bbs-user", username)
  localStorage.setItem("norm-bbs-session", token)
  renderAccount()
}

function logout() {
  state.username = ""
  state.token = ""
  localStorage.removeItem("norm-bbs-user")
  localStorage.removeItem("norm-bbs-session")
  renderAccount()
}

function renderAccount() {
  const loggedIn = Boolean(state.token)
  elements.authPanel.hidden = loggedIn
  elements.boardForm.hidden = !loggedIn
  elements.composeButton.hidden = !loggedIn
  elements.replyForm.hidden = !loggedIn
  elements.account.replaceChildren()
  if (!loggedIn) {
    elements.account.textContent = "访客"
    return
  }
  const name = document.createElement("strong")
  name.textContent = state.username
  const button = document.createElement("button")
  button.className = "secondary"
  button.textContent = "退出"
  button.addEventListener("click", logout)
  elements.account.append(name, button)
}

function renderBoards() {
  elements.boards.replaceChildren()
  const all = document.createElement("button")
  all.className = `board${state.selectedBoard === null ? " active" : ""}`
  all.textContent = "全部主题"
  all.addEventListener("click", () => selectBoard(null))
  elements.boards.append(all)
  for (const board of state.boards) {
    const button = document.createElement("button")
    button.className = `board${state.selectedBoard === board.id ? " active" : ""}`
    button.textContent = board.name
    button.addEventListener("click", () => selectBoard(board.id))
    elements.boards.append(button)
  }
  elements.boardSelect.replaceChildren(...state.boards.map(board => {
    const option = document.createElement("option")
    option.value = board.id
    option.textContent = board.name
    return option
  }))
}

function selectBoard(id) {
  state.selectedBoard = id
  state.page = 0
  renderBoards()
  loadTopics()
}

function renderTopics(page) {
  const topics = (page.content || []).filter(topic => state.selectedBoard === null || topic.boardId === state.selectedBoard)
  elements.topics.replaceChildren()
  if (!topics.length) {
    const empty = document.createElement("div")
    empty.className = "empty"
    empty.textContent = "这里还没有主题，来写第一篇吧。"
    elements.topics.append(empty)
  }
  for (const topic of topics) {
    const card = document.createElement("article")
    card.className = "topic-card"
    const byline = document.createElement("span")
    byline.className = "topic-byline"
    byline.textContent = `${topic.author} · ${boardName(topic.boardId)}`
    const title = document.createElement("h3")
    title.textContent = topic.title
    const content = document.createElement("p")
    content.textContent = topic.content.length > 150 ? `${topic.content.slice(0, 150)}…` : topic.content
    card.append(byline, title, content)
    card.addEventListener("click", () => openTopic(topic.id))
    elements.topics.append(card)
  }
  const size = page.pageable?.size || 10
  state.totalPages = Math.max(1, Math.ceil((page.totalSize || 0) / size))
  elements.pageLabel.textContent = `${state.page + 1} / ${state.totalPages}`
  elements.previousPage.disabled = state.page === 0
  elements.nextPage.disabled = state.page + 1 >= state.totalPages
}

function boardName(id) {
  return state.boards.find(board => board.id === id)?.name || "未分类"
}

async function loadBoards() {
  state.boards = await api("/boards") || []
  renderBoards()
}

async function loadTopics() {
  try { renderTopics(await api(`/topics?page=${state.page}&size=10`)) }
  catch (error) { notify(error.message, true) }
}

async function openTopic(id) {
  try {
    const [topic, replies] = await Promise.all([api(`/topics/${id}`), api(`/topics/${id}/replies`)])
    state.currentTopic = topic
    elements.topicTitle.textContent = topic.title
    elements.topicMeta.textContent = `${topic.author} · ${boardName(topic.boardId)}`
    elements.topicContent.textContent = topic.content
    renderReplies(replies || [])
    elements.topicDialog.showModal()
  } catch (error) { notify(error.message, true) }
}

function renderReplies(replies) {
  elements.replies.replaceChildren()
  elements.replyCount.textContent = `${replies.length} 条`
  if (!replies.length) {
    const empty = document.createElement("p")
    empty.className = "empty"
    empty.textContent = "还没有回复。"
    elements.replies.append(empty)
  }
  for (const reply of replies) {
    const item = document.createElement("article")
    item.className = "reply"
    const author = document.createElement("strong")
    author.textContent = reply.author
    const content = document.createElement("p")
    content.textContent = reply.content
    item.append(author, content)
    elements.replies.append(item)
  }
}

async function credentials(form) {
  const data = new FormData(form)
  return { username: data.get("username"), password: data.get("password") }
}

document.getElementById("registerForm").addEventListener("submit", async event => {
  event.preventDefault()
  try {
    const body = await credentials(event.currentTarget)
    await api("/users", { method: "POST", body: JSON.stringify(body) })
    notify("账号已创建，现在可以登录。")
    document.querySelector('#loginForm [name="username"]').value = body.username
  } catch (error) { notify(error.message, true) }
})

document.getElementById("loginForm").addEventListener("submit", async event => {
  event.preventDefault()
  try {
    const body = await credentials(event.currentTarget)
    const token = await api("/sessions", { method: "POST", body: JSON.stringify(body) })
    if (!token) throw new Error("用户名或密码不正确")
    setSession(body.username, token)
    notify(`欢迎回来，${body.username}。`)
  } catch (error) { notify(error.message, true) }
})

elements.boardForm.addEventListener("submit", async event => {
  event.preventDefault()
  const data = new FormData(event.currentTarget)
  try {
    await api("/session/boards", { method: "POST", body: JSON.stringify({ name: data.get("name") }) })
    event.currentTarget.reset()
    await loadBoards()
  } catch (error) { notify(error.message, true) }
})

elements.composeButton.addEventListener("click", () => {
  if (!state.boards.length) return notify("请先创建一个板块。", true)
  elements.composeDialog.showModal()
})

elements.topicForm.addEventListener("submit", async event => {
  event.preventDefault()
  const data = new FormData(event.currentTarget)
  try {
    await api("/session/topics", { method: "POST", body: JSON.stringify({ boardId: Number(data.get("boardId")), title: data.get("title"), content: data.get("content") }) })
    elements.composeDialog.close()
    event.currentTarget.reset()
    await loadTopics()
    notify("主题已发布。")
  } catch (error) { notify(error.message, true) }
})

elements.replyForm.addEventListener("submit", async event => {
  event.preventDefault()
  const data = new FormData(event.currentTarget)
  try {
    await api("/session/replies", { method: "POST", body: JSON.stringify({ topicId: state.currentTopic.id, content: data.get("content") }) })
    event.currentTarget.reset()
    await openTopic(state.currentTopic.id)
  } catch (error) { notify(error.message, true) }
})

elements.closeTopic.addEventListener("click", () => elements.topicDialog.close())
elements.previousPage.addEventListener("click", () => { state.page--; loadTopics() })
elements.nextPage.addEventListener("click", () => { state.page++; loadTopics() })

async function start() {
  renderAccount()
  if (state.token) {
    try { state.username = await api("/session/profile") }
    catch { logout() }
  }
  await Promise.all([loadBoards(), loadTopics()])
}

start()
