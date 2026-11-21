import React from 'react'
import { renderAmisSchema } from 'ouroboros-sdk'

class ContainerControlButtons extends React.Component {
  constructor(props) {
    super(props)
    this.containerBtnsRef = React.createRef()
    this.renderHandle = null
  }
  async componentDidMount() {
    this.renderContent()
  }
  async componentDidUpdate(prevProps) {
    if (prevProps.profile !== this.props.profile) {
      await this.renderContent()
    }
  }
  componentWillUnmount() {
    this.renderHandle?.destroy?.()
    this.renderHandle = null
  }
  async renderContent() {
    if (!this.props.profile) {
      this.renderHandle?.destroy?.()
      this.renderHandle = null
      if (this.containerBtnsRef.current) {
        this.containerBtnsRef.current.innerHTML = ''
      }
      return
    }
    const schema = {
      type: 'amis-schema',
      schemaId: '/dev/container-control-buttons',
    }
    this.renderHandle?.destroy?.()
    this.renderHandle = await renderAmisSchema(this.containerBtnsRef.current, schema, {
      data: {
        profile: this.props.profile,
      },
    })
  }
  render() {
    return (
      <div>
        <div ref={this.containerBtnsRef}></div>
      </div>
    )
  }
}
export default ContainerControlButtons
