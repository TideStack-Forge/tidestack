import React from 'react'

export default function SvgIcon({ icon }) {
  return (
    <svg className="svg-icon" aria-hidden="true">
      <use xlinkHref={`#icon-${icon || ''}`} />
    </svg>
  )
}
