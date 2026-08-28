// 1. Service Worker Registration
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('./sw.js')
    .then(() => console.log('Service Worker Registered'))
    .catch((err) => console.log('Service Worker registration failed: ', err));
}

// 2. Handle PWA Installation Promotion
let deferredPrompt;
const pwaBanner = document.getElementById('pwaBanner');
const installBtn = document.getElementById('installBtn');

window.addEventListener('beforeinstallprompt', (e) => {
  // Prevent Chrome 67 and earlier from automatically showing the prompt
  e.preventDefault();
  // Stash the event so it can be triggered later.
  deferredPrompt = e;
  // Update UI notify the user they can install the PWA
  pwaBanner.style.display = 'flex';
});

installBtn.addEventListener('click', async () => {
  if (!deferredPrompt) return;
  // Show the install prompt
  deferredPrompt.prompt();
  // Wait for the user to respond to the prompt
  const { outcome } = await deferredPrompt.userChoice;
  console.log(`User response to the install prompt: ${outcome}`);
  // We've used the prompt, and can't use it again, discard it
  deferredPrompt = null;
  // Hide our install banner
  pwaBanner.style.display = 'none';
});

// 3. Mock Data
let userCredits = parseInt(localStorage.getItem('userCredits') || '12');

const mockClasses = [
  {
    id: 1,
    name: 'CrossFit WOD',
    type: 'CrossFit',
    time: '08:00 - 09:00',
    coach: 'John Doe',
    attendees: 14,
    capacity: 20,
    booked: false
  },
  {
    id: 2,
    name: 'Pilates Core',
    type: 'Pilates',
    time: '10:30 - 11:30',
    coach: 'Sarah Connor',
    attendees: 8,
    capacity: 12,
    booked: false
  },
  {
    id: 3,
    name: 'Heavy Strength',
    type: 'Strength Training',
    time: '18:00 - 19:15',
    coach: 'Marcus Aurelius',
    attendees: 6,
    capacity: 15,
    booked: false
  }
];

// Load saved booking states
const savedBookings = JSON.parse(localStorage.getItem('savedBookings') || '[]');
mockClasses.forEach(c => {
  if (savedBookings.includes(c.id)) {
    c.booked = true;
    c.attendees += 1;
  }
});

// Render Credits
function updateCreditsDisplay() {
  document.getElementById('creditsDisplay').innerText = `${userCredits} Credits`;
  localStorage.setItem('userCredits', userCredits.toString());
}

// Render Classes List
function renderClasses() {
  const container = document.getElementById('classList');
  container.innerHTML = '';

  mockClasses.forEach(item => {
    const card = document.createElement('div');
    card.className = 'class-card';
    card.innerHTML = `
      <div class="class-header">
        <div>
          <span class="class-type-badge">${item.type}</span>
          <h2 class="class-name">${item.name}</h2>
        </div>
        <span class="class-capacity">${item.attendees}/${item.capacity} booked</span>
      </div>
      <div class="class-details">
        <div class="class-detail-item">
          <!-- Time Icon -->
          <svg viewBox="0 0 24 24"><path d="M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10 10-4.5 10-10S17.5 2 12 2zm0 18c-4.4 0-8-3.6-8-8s3.6-8 8-8 8 3.6 8 8-3.6 8-8 8zm.5-13H11v6l5.2 3.2.8-1.3-4.5-2.7V7z"/></svg>
          <span>${item.time}</span>
        </div>
        <div class="class-detail-item">
          <!-- Coach Icon -->
          <svg viewBox="0 0 24 24"><path d="M12 2c1.1 0 2 .9 2 2s-.9 2-2 2-2-.9-2-2 .9-2 2-2zm9 7h-6v13h-2v-6h-2v6H9V9H3V7h18v2z"/></svg>
          <span>Coach: ${item.coach}</span>
        </div>
      </div>
      <button class="book-btn ${item.booked ? 'btn-cancel' : 'btn-book'}">
        ${item.booked ? 'Cancel Booking' : 'Book Class'}
      </button>
    `;

    // Bind Button Click
    const btn = card.querySelector('.book-btn');
    btn.addEventListener('click', () => toggleBooking(item));

    container.appendChild(card);
  });
}

// Booking logic
function toggleBooking(item) {
  if (item.booked) {
    // Cancel Booking
    item.booked = false;
    item.attendees -= 1;
    userCredits += 1;
    showToast('Booking cancelled.', 'info');
  } else {
    // Book Class
    if (userCredits <= 0) {
      showToast('No credits remaining!', 'error');
      return;
    }
    item.booked = true;
    item.attendees += 1;
    userCredits -= 1;
    showToast(`Booked! You're in for ${item.name}.`, 'success');
  }

  // Save State
  const activeBookings = mockClasses.filter(c => c.booked).map(c => c.id);
  localStorage.setItem('savedBookings', JSON.stringify(activeBookings));
  
  updateCreditsDisplay();
  renderClasses();
}

// Toast System
function showToast(message, type) {
  const toast = document.getElementById('toast');
  const icon = document.getElementById('toastIcon');
  const msg = document.getElementById('toastMsg');

  msg.innerText = message;
  
  if (type === 'success') {
    icon.innerHTML = '🟢';
    toast.style.borderColor = 'rgba(52, 199, 89, 0.3)';
  } else if (type === 'error') {
    icon.innerHTML = '🔴';
    toast.style.borderColor = 'rgba(255, 59, 48, 0.3)';
  } else {
    icon.innerHTML = '🔵';
    toast.style.borderColor = 'rgba(0, 127, 245, 0.3)';
  }

  toast.classList.add('show');
  
  setTimeout(() => {
    toast.classList.remove('show');
  }, 2500);
}

// Initial Run
updateCreditsDisplay();
renderClasses();
