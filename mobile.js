// ============ Mobile Navigation ============
// بيضيف زرار الهامبرجر وقائمة الموبايل المنسدلة تلقائياً،
// من غير ما تحتاجي تعدّلي أي حاجة في الـ HTML.
// لازم يتحط في الصفحة بعد script.js (أو قبله، مش فارق، لأنه مستقل تماماً)

(function () {
  const header = document.querySelector("header");
  if (!header) return;

  const nav = header.querySelector("nav");
  const cvBtn = header.querySelector(".cv-btn");

  // -------- زرار الهامبرجر --------
  const menuToggle = document.createElement("button");
  menuToggle.className = "mobile-menu-toggle";
  menuToggle.setAttribute("aria-label", "Open menu");
  menuToggle.innerHTML = `<i class="fa-solid fa-bars"></i>`;
  header.appendChild(menuToggle);

  // -------- لوحة القائمة المنسدلة --------
  const mobileMenu = document.createElement("div");
  mobileMenu.className = "mobile-menu";
  mobileMenu.innerHTML = `
    <button class="mobile-menu-close" aria-label="Close menu">
      <i class="fa-solid fa-xmark"></i>
    </button>
    <nav class="mobile-menu-nav">${nav ? nav.innerHTML : ""}</nav>
    ${
      cvBtn
        ? `<a href="${cvBtn.getAttribute("href")}" download class="cv-btn mobile-menu-cv">${cvBtn.textContent.trim()}</a>`
        : ""
    }
  `;
  document.body.appendChild(mobileMenu);

  // -------- فتح / قفل --------
  function openMenu() {
    mobileMenu.classList.add("active");
    document.body.style.overflow = "hidden";
  }

  function closeMenu() {
    mobileMenu.classList.remove("active");
    document.body.style.overflow = "";
  }

  menuToggle.addEventListener("click", openMenu);
  mobileMenu.querySelector(".mobile-menu-close").addEventListener("click", closeMenu);

  // قفل القائمة تلقائياً لما تدوسي على أي رابط جواها
  mobileMenu.querySelectorAll("a").forEach((link) => {
    link.addEventListener("click", closeMenu);
  });
})();
