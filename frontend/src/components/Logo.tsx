function Logo() {
  return (
    <svg className="logo" viewBox="0 0 100 100" aria-hidden="true">
      <g transform="translate(-2 -6)" fill="none" stroke="currentColor" strokeWidth="7" strokeLinecap="round">
        <path d="M16 22 H46 A17 17 0 0 1 46 56 L73.6 77.1" strokeLinejoin="round" />
        <path d="M28 22 V74" />
        <circle className="logo-wheel" cx="28" cy="82" r="8" />
        <circle className="logo-wheel" cx="80" cy="82" r="8" />
      </g>
    </svg>
  )
}

export default Logo
