package org.yamcs.tctm.ccsds;

import java.io.IOException;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.logging.Log;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.tctm.PacketPreprocessor;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.TmPacketDataLink;
import org.yamcs.tctm.TmSink;
import org.yamcs.utils.YObjectLoader;

/**
 * Handles packets from one VC
 * 
 * @author nm
 *
 */
public class VirtualChannelPacketHandler implements TmPacketDataLink, VirtualChannelHandler {
    TmSink tmSink;
    private long numPackets;
    volatile boolean disabled = false;
    long lastFrameSeq = -1;
    EventProducer eventProducer;
    int packetLostCount;
    private final Log log;
    PacketDecoder packetDecoder;
    long idleFrameCount = 0;
    PacketPreprocessor packetPreprocessor;
    final String name;
    final VcManagedParameters vmp;

    AggregatedDataLink parent;

    public VirtualChannelPacketHandler(String yamcsInstance, String name, VcManagedParameters vmp) {
        this.vmp = vmp;
        this.name = name;

        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, this.getClass().getSimpleName(), 10000);
        log = new Log(this.getClass(), yamcsInstance);

        packetDecoder = new PacketDecoder(vmp.maxPacketLength, p -> handlePacket(p));
        packetDecoder.stripEncapsulationHeader(vmp.stripEncapsulationHeader);

        try {
            if (vmp.packetPreprocessorArgs != null) {
                packetPreprocessor = YObjectLoader.loadObject(vmp.packetPreprocessorClassName, yamcsInstance,
                        vmp.packetPreprocessorArgs);
            } else {
                packetPreprocessor = YObjectLoader.loadObject(vmp.packetPreprocessorClassName, yamcsInstance);
            }
        } catch (ConfigurationException e) {
            log.error("Cannot instantiate the packet preprocessor", e);
            throw e;
        } catch (IOException e) {
            log.error("Cannot instantiate the packetInput stream", e);
            throw new ConfigurationException(e);
        }
    }

    @Override
    public void handle(TransferFrame frame) {
        if (disabled) {
            log.trace("Dropping frame for VC {} because the link is disabled", frame.getVirtualChannelId());
            return;
        }

        if (frame.containsOnlyIdleData()) {
            if (log.isTraceEnabled()) {
                log.trace("Dropping idle frame for VC {}", frame.getVirtualChannelId());
            }
            idleFrameCount++;
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("Processing frame VC {}, SEQ {}, FHP {}, DS {}, DE {}", frame.getVirtualChannelId(),
                    frame.getVcFrameSeq(),
                    frame.getFirstHeaderPointer(), frame.getDataStart(), frame.getDataEnd());
        }

        int dataStart = frame.getDataStart();
        int packetStart = frame.getFirstHeaderPointer();
        int dataEnd = frame.getDataEnd();
        byte[] data = frame.getData();

        try {
            int frameLoss = frame.lostFramesCount(lastFrameSeq);
            lastFrameSeq = frame.getVcFrameSeq();

            if (packetDecoder.hasIncompletePacket()) {
                if (frameLoss != 0) {
                    log.warn("Incomplete packet dropped because of frame loss ");
                    packetDecoder.reset();
                } else {
                    if (packetStart != -1) {
                        packetDecoder.process(data, dataStart, packetStart - dataStart);
                    } else {
                        packetDecoder.process(data, dataStart, dataEnd - dataStart);
                    }
                }
            }
            if (packetStart != -1) {
                if (packetDecoder.hasIncompletePacket()) {
                    eventProducer
                            .sendWarning("Incomplete packet decoded when reaching the beginning of another packet");
                    packetDecoder.reset();
                }
                packetDecoder.process(data, packetStart, dataEnd - packetStart);
            }
        } catch (TcTmException e) {
            packetDecoder.reset();
            eventProducer.sendWarning(e.toString());
        }
    }

    private void handlePacket(byte[] p) {
        if (log.isTraceEnabled()) {
            log.trace("VC {}, SEQ {} decoded packet of length {}", vmp.vcId, lastFrameSeq, p.length);
        }

        numPackets++;
        PacketWithTime pwt = packetPreprocessor.process(p);
        if (pwt != null) {
            tmSink.processPacket(pwt);
        }
    }

    @Override
    public Status getLinkStatus() {
        return disabled ? Status.DISABLED : Status.OK;
    }

    @Override
    public String getDetailedStatus() {
        return null;
    }

    @Override
    public void enable() {
        this.disabled = false;
    }

    @Override
    public void disable() {
        this.disabled = true;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public long getDataInCount() {
        return numPackets;
    }

    @Override
    public long getDataOutCount() {
        return 0;
    }

    @Override
    public void resetCounters() {
        numPackets = 0;
    }

    @Override
    public void setTmSink(TmSink tmSink) {
        this.tmSink = tmSink;
    }

    @Override
    public YConfiguration getConfig() {
        return vmp.config;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public AggregatedDataLink getParent() {
        return parent;
    }

    @Override
    public void setParent(AggregatedDataLink parent) {
        this.parent = parent;
    }
}
