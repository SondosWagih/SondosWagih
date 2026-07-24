// ============ تبديل الوضع الفاتح / الغامق ============

const root = document.documentElement;
const toggleBtn = document.getElementById('themeToggle');

// استرجاع آخر اختيار محفوظ، أو استخدام وضع الجهاز كافتراضي
const saved = localStorage.getItem('son-theme');
const prefersLight = window.matchMedia('(prefers-color-scheme: light)').matches;
const initial = saved || (prefersLight ? 'light' : 'dark');
root.setAttribute('data-theme', initial);

toggleBtn.addEventListener('click', () => {
  const current = root.getAttribute('data-theme');
  const next = current === 'dark' ? 'light' : 'dark';
  root.setAttribute('data-theme', next);
  localStorage.setItem('son-theme', next);
});

// ============ Works Carousel ============

// المفتاح ده لازم يتطابق مع نفس المفتاح المستخدم في works-manager.html
const WORKS_STORAGE_KEY = "son-works-v1";

// دي القيم الافتراضية (fallback) اللي بتتستخدم لو مفيش حاجة محفوظة في
// localStorage لسه (أول مرة تفتحي الموقع)، أو لو فتح حد الموقع من
// متصفح تاني/جهاز تاني معندوش نسخة الـ localStorage بتاعتك.
// zoom هنا نسبة مئوية: 100 = مطابق تماماً لـ cover العادي، أكتر من كده
// = تقريب زوم إن، أقل من 100 = تبعيد زوم أوت.
const defaultWorksData = [
  { title: "Magazine Cover", rating: 5, image: "image/4.jpg", position: "50% 23%", zoom: 75 },
  { title: "Product Retouch", rating: 4, image: "image/2.jpg", position: "50% 50%", zoom: 100 },
  { title: "Brand Poster", rating: 4, image: "image/3.jpg", position: "50% 28%", zoom: 74 },
  { title: "Social Media Set", rating: 4, image: "image/1.jpg", position: "50% 36%", zoom: 100 },
];

// -------- تحميل بيانات Works: بنفضّل النسخة المحفوظة من أداة
// works-manager.html (لو موجودة وصحيحة)، وإلا بنرجع للقيم الافتراضية.
// ملحوظة مهمة: الـ localStorage ده بيبقى محفوظ جوه المتصفح بتاعك على
// جهازك بس، مش على السيرفر. يعني ده مفيد كمعاينة فورية وانتِ شغالة على
// جهازك، لكن لو نشرتي الموقع أونلاين، الزوار (وأي جهاز/متصفح تاني) هيشوفوا
// القيم الافتراضية اللي في الكود، مش تعديلاتك المحفوظة محلياً. عشان
// تعديلاتك تبان لكل الزوار، لازم كمان تنسخي الكود الجاهز من الأداة
// وتستبدلي بيه defaultWorksData فوق قبل ما ترفعي الموقع.
function loadWorksData() {
  try {
    const raw = localStorage.getItem(WORKS_STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed) && parsed.length) return parsed;
    }
  } catch (e) {
    // بيانات تالفة في الـ localStorage، نتجاهلها ونرجع للافتراضي
  }
  return defaultWorksData;
}

let worksData = loadWorksData();

let currentIndex = 0;
let isAnimating = false;

// العناصر التلاتة الثابتة في الصفحة — بس "الدور" بتاعهم (left/center/right)
// بيتبدّل بين بعضهم مع كل نقلة
let elLeft   = document.getElementById("slideLeft");
let elCenter = document.getElementById("slideCenter");
let elRight  = document.getElementById("slideRight");

// الـ footer (العنوان + النجوم) هيتنقل فعلياً للعنصر اللي بيبقى center جديد
const worksFooterEl = elCenter.querySelector(".works-footer");

function mod(n, m) {
  return ((n % m) + m) % m;
}

function renderStars(container, rating) {
  container.innerHTML = "";
  for (let i = 1; i <= 5; i++) {
    const star = document.createElement("i");
    star.className = i <= rating ? "fa-solid fa-star filled" : "fa-solid fa-star";
    container.appendChild(star);
  }
}

// -------- Zoom / cover-fit helpers --------
// بنجيب المقاس الحقيقي للصورة، وبنحسب المقاس اللي يخليها تغطي الإطار
// بالكامل (زي background-size: cover)، وبعدين بنكبّره/بنصغّره حسب
// نسبة الزوم المطلوبة (100 = زي ما هي، فوق كده تقريب، تحت كده تبعيد)
const dimCache = new Map();
function loadDims(src) {
  return new Promise((resolve) => {
    if (!src) return resolve(null);
    if (dimCache.has(src)) return resolve(dimCache.get(src));
    const img = new Image();
    img.onload = () => {
      const d = { w: img.naturalWidth, h: img.naturalHeight };
      dimCache.set(src, d);
      resolve(d);
    };
    img.onerror = () => resolve(null);
    img.src = src;
  });
}

function computeCoverSize(cw, ch, iw, ih, zoomPct) {
  const containerRatio = cw / ch;
  const imgRatio = iw / ih;
  let baseW, baseH;
  if (imgRatio > containerRatio) {
    baseH = ch;
    baseW = ch * imgRatio;
  } else {
    baseW = cw;
    baseH = cw / imgRatio;
  }
  const scale = (zoomPct || 100) / 100;
  return { w: baseW * scale, h: baseH * scale };
}

// بيفتكر آخر item اتعرض في كل عنصر (left/center/right)، عشان نقدر نعيد
// حساب الزوم بعد أي تغيير في حجم الشاشة (resize) من غير ما نلخبط الكارت
// اللي واقف عليه المستخدم لو غيّر الصورة أثناء التحميل
const displayedItem = new WeakMap();

// كل كارت works-image بيتكوّن من طبقتين جواه (بيتحطوا مرة واحدة بس ويتعاد
// استخدامهم بعد كده):
// - works-image-bg: نسخة مبلورة بتغطي الإطار بالكامل دايماً (زوم 100%)،
//   وده اللي بيبان في أي حواف فاضية لو الزوم الفعلي أقل من 100% (زوم أوت)،
//   بدل ما تبان مسافة فاضية بلون رمادي عادي
// - works-image-fg: الصورة الحقيقية اللي بتتزوم/تتحرك حسب position/zoom
function ensureImageLayers(imgEl) {
  let bg = imgEl.querySelector(".works-image-bg");
  let fg = imgEl.querySelector(".works-image-fg");

  if (getComputedStyle(imgEl).position === "static") {
    imgEl.style.position = "relative";
  }
  imgEl.style.overflow = "hidden";

  if (!bg) {
    bg = document.createElement("div");
    bg.className = "works-image-bg";
    Object.assign(bg.style, {
      position: "absolute",
      inset: "0",
      backgroundRepeat: "no-repeat",
      backgroundSize: "cover",
      backgroundPosition: "center",
      filter: "blur(18px)",
      transform: "scale(1.15)", // عشان حواف البلور المتشفّفة متبانش
      pointerEvents: "none",
    });
    imgEl.insertBefore(bg, imgEl.firstChild);
  }

  if (!fg) {
    fg = document.createElement("div");
    fg.className = "works-image-fg";
    Object.assign(fg.style, {
      position: "absolute",
      inset: "0",
      backgroundRepeat: "no-repeat",
      pointerEvents: "none",
    });
    imgEl.insertBefore(fg, bg.nextSibling);
  }

  return { bg, fg };
}

async function applyFit(cardEl, item) {
  const el = cardEl.querySelector(".works-image");
  if (!el || !item || !item.image) return;

  displayedItem.set(cardEl, item);
  const { bg, fg } = ensureImageLayers(el);

  bg.style.backgroundImage = `url(${item.image})`;

  fg.style.backgroundImage = `url(${item.image})`;
  fg.style.backgroundPosition = item.position || "50% 50%";
  fg.style.backgroundSize = "cover"; // fallback فوري لحد ما نحسب الزوم الدقيق

  const dims = await loadDims(item.image);
  // لو العنصر اتغيّر لصورة تانية قبل ما التحميل يخلص، نتجاهل النتيجة
  if (displayedItem.get(cardEl) !== item) return;

  const cw = el.clientWidth;
  const ch = el.clientHeight;
  if (!dims || !cw || !ch) return;

  const { w, h } = computeCoverSize(cw, ch, dims.w, dims.h, item.zoom);
  fg.style.backgroundSize = `${w}px ${h}px`;
}

function setImage(cardEl, item) {
  if (item && item.image) {
    applyFit(cardEl, item);
  }
}

function updateFooter(item) {
  document.getElementById("worksTitle").textContent = item.title;
  renderStars(document.getElementById("worksRating"), item.rating);
}

// أول رندر لما الصفحة تفتح
function initWorksCarousel() {
  const total = worksData.length;
  currentIndex = 0;
  setImage(elLeft,   worksData[mod(currentIndex - 1, total)]);
  setImage(elCenter, worksData[currentIndex]);
  setImage(elRight,  worksData[mod(currentIndex + 1, total)]);
  updateFooter(worksData[currentIndex]);
}

function goToWork(dir) {
  if (isAnimating) return;
  isAnimating = true;

  const total = worksData.length;
  const newIndex = mod(currentIndex + dir, total);

  const oldLeft = elLeft;
  const oldCenter = elCenter;
  const oldRight = elRight;

  // تحديد الأدوار الجديدة بعد النقلة
  let newLeft, newCenter, newRight;
  if (dir === 1) {
    newLeft   = oldCenter;
    newCenter = oldRight;
    newRight  = oldLeft; // ده اللي هيتدوّر عليه محتوى جديد لسه ما اتشافش
  } else {
    newRight  = oldCenter;
    newCenter = oldLeft;
    newLeft   = oldRight; // نفس الفكرة في الاتجاه التاني
  }

  // العنصر اللي بيتدوّر عليه (المُعاد استخدامه) ياخد صورة المشروع
  // الجديد قبل ما يتحرك، عشان يبان بالمحتوى الصح وهو بيتحرك
  const recycledEl = dir === 1 ? newRight : newLeft;
  const recycledData = dir === 1
    ? worksData[mod(newIndex + 1, total)]
    : worksData[mod(newIndex - 1, total)];
  setImage(recycledEl, recycledData);

  // نقل الـ footer فعلياً للعنصر اللي بقى هو الـ center الجديد
  newCenter.appendChild(worksFooterEl);
  updateFooter(worksData[newIndex]);

  // تبديل الـ classes — الـ CSS transition الموجودة أصلاً هي اللي هتعمل الحركة
  requestAnimationFrame(() => {
    newLeft.classList.remove("works-center", "works-right");
    newLeft.classList.add("works-left");

    newCenter.classList.remove("works-left", "works-right");
    newCenter.classList.add("works-center");

    newRight.classList.remove("works-left", "works-center");
    newRight.classList.add("works-right");
  });

  let finished = false;
  function finish() {
    if (finished) return;
    finished = true;
    newCenter.removeEventListener("transitionend", onEnd);

    elLeft = newLeft;
    elCenter = newCenter;
    elRight = newRight;
    currentIndex = newIndex;
    isAnimating = false;
  }

  function onEnd(e) {
    if (e.target === newCenter && e.propertyName === "transform") finish();
  }
  newCenter.addEventListener("transitionend", onEnd);

  // fallback احتياطي لو transitionend ما اتفعّلش لأي سبب
  setTimeout(finish, 700);
}

document.getElementById("worksPrev").addEventListener("click", () => goToWork(-1));
document.getElementById("worksNext").addEventListener("click", () => goToWork(1));

initWorksCarousel();

// لو حجم الشاشة اتغيّر (تدوير الموبايل، تصغير/تكبير النافذة) نعيد حساب
// مقاس الزوم عشان يفضل مطابق للإطار الجديد
let worksResizeTimer;
window.addEventListener("resize", () => {
  clearTimeout(worksResizeTimer);
  worksResizeTimer = setTimeout(() => {
    [elLeft, elCenter, elRight].forEach((cardEl) => {
      const item = displayedItem.get(cardEl);
      if (item) applyFit(cardEl, item);
    });
  }, 150);
});

// لو المستخدمة معدّلة في works-manager.html وهي فاتحة تبويب تاني في نفس
// المتصفح، الـ tab بتاع الموقع نفسه ممكن يستقبل حدث "storage" ده ويعيد
// تحميل الكاروسيل تلقائياً من غير ما تحتاج تعمل refresh يدوي
window.addEventListener("storage", (e) => {
  if (e.key !== WORKS_STORAGE_KEY) return;
  worksData = loadWorksData();
  initWorksCarousel();
});

// ============ Lightbox ============
const lightbox = document.getElementById("lightbox");
const lightboxImg = lightbox.querySelector("img");
const lightboxCloseBtn = document.getElementById("lightboxClose");

function openLightbox(src) {
  lightboxImg.src = src;
  lightbox.classList.add("active");
  document.body.style.overflow = "hidden"; // يمنع سكرول الصفحة اللي وراها
}

function closeLightbox() {
  lightbox.classList.remove("active");
  document.body.style.overflow = "";
}

// قفل بزرار X
lightboxCloseBtn.addEventListener("click", closeLightbox);

// قفل بالضغط على أي مكان فاضي حوالين الصورة (مش على الصورة نفسها)
lightbox.addEventListener("click", (e) => {
  if (e.target === lightbox) closeLightbox();
});

// قفل بزرار Esc
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") closeLightbox();
});

// فتح الصورة المركزية فقط في كاروسيل الـ works
// (بالضغط على الصورة نفسها أو على زرار الـ expand، الاتنين جوه .works-image)
document.querySelector(".works-stage").addEventListener("click", (e) => {
  const centerCard = e.target.closest(".works-center");
  if (!centerCard) return; // الصور الجانبية مش قابلة للفتح

  if (e.target.closest(".works-image")) {
    openLightbox(worksData[currentIndex].image);
  }
});

// ============ Contact Form ============

const contactForm = document.getElementById("contactForm");
const submitBtn = contactForm.querySelector(".contact-submit");

const CONTACT_API_URL = "https://script.google.com/macros/s/AKfycbxJPW4-XBp_eL85U9geQl1-QBkst-Rbx2stT3S2wxbNn4la7VsG0adQXhp5OLZIGXh4aQ/exec";

// -------- Toast (site-styled notification, replaces alert()) --------
function showToast(type, message) {
  const existing = document.querySelector(".toast");
  if (existing) existing.remove();

  const toast = document.createElement("div");
  toast.className = `toast toast-${type}`;
  toast.innerHTML = `
    <span class="toast-icon">${type === "success" ? "✓" : "!"}</span>
    <span class="toast-message">${message}</span>
    <button class="toast-close" aria-label="Close">&times;</button>
  `;
  document.body.appendChild(toast);

  requestAnimationFrame(() => toast.classList.add("show"));

  const remove = () => {
    toast.classList.remove("show");
    setTimeout(() => toast.remove(), 350);
  };

  toast.querySelector(".toast-close").addEventListener("click", remove);
  setTimeout(remove, 4500);
}

// -------- Reference ID generator --------
function generateRefId() {
  if (crypto.randomUUID) return crypto.randomUUID();
  // fallback للمتصفحات القديمة جداً
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

// -------- المرحلة 2: التحقق الفعلي من وجود الصف في الشيت --------
// بيحاول كذا مرة بفاصل زمني بسيط، لأن الكتابة في الشيت ممكن تاخد جزء
// من الثانية لحد ما تظهر في القراءة التالية
async function verifyRowSaved(refId, attempts = 5, delayMs = 700) {
  for (let i = 0; i < attempts; i++) {
    await new Promise((r) => setTimeout(r, delayMs));
    try {
      const res = await fetch(`${CONTACT_API_URL}?refId=${encodeURIComponent(refId)}`);
      const data = await res.json();
      if (data.found) return true;
    } catch (err) {
      // فشل المحاولة دي، جربي تاني في اللفة الجاية
    }
  }
  return false;
}

// -------- Submit handler --------
contactForm.addEventListener("submit", async function (e) {
  e.preventDefault();

  const name = document.getElementById("contactName").value.trim();
  const email = document.getElementById("contactEmail").value.trim();
  const message = document.getElementById("contactMessage").value.trim();
  const company = document.getElementById("contactCompany").value.trim(); // honeypot

  if (!name || !email || !message) {
    showToast("error", "Please fill in all fields before sending.");
    return;
  }

  const originalText = submitBtn.textContent;
  submitBtn.disabled = true;
  submitBtn.textContent = "Sending...";

  const refId = generateRefId();

  try {
    // -------- المرحلة 1: إرسال البيانات --------
    // no-cors عشان ما نعتمدش على قراءة رد الـ POST خالص (ده اللي كان بيفشل
    // أحياناً حسب المتصفح/الجهاز بسبب الـ redirect بتاع Google Apps Script)
    await fetch(CONTACT_API_URL, {
      method: "POST",
      mode: "no-cors",
      headers: { "Content-Type": "text/plain;charset=utf-8" },
      body: JSON.stringify({ name, email, message, company, refId }),
    });

    // -------- المرحلة 2: التأكيد الحقيقي --------
    const saved = await verifyRowSaved(refId);

    if (saved) {
      showToast("success", "Your message has been sent successfully!");
      contactForm.reset();
    } else {
      showToast("error", "We couldn't confirm your message was received. Please try again.");
    }
  } catch (err) {
    showToast("error", "Could not reach the server. Please check your connection.");
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = originalText;
  }
});